package com.example.capstone.service;

import com.example.capstone.entity.ReceiverMapping;
import com.example.capstone.entity.Slip;
import com.example.capstone.entity.User;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.messaging.SlipOcrListener;
import com.example.capstone.messaging.SlipOcrMessage;
import com.example.capstone.messaging.SlipOcrPublisher;
import com.example.capstone.repository.DebtRepository;
import com.example.capstone.repository.SlipRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The asynchronous slip OCR pipeline: enqueue, consume (idempotent, retryable) and dead-letter handling. */
@ExtendWith(MockitoExtension.class)
class SlipAsyncPipelineTest {

    @Mock
    private MinioService minioService;
    @Mock
    private OcrService ocrService;
    @Mock
    private SlipRepository slipRepository;
    @Mock
    private ReceiverMappingService receiverMappingService;
    @Mock
    private RepaymentService repaymentService;
    @Mock
    private TransactionService transactionService;
    @Mock
    private DebtRepository debtRepository;
    @Mock
    private ObjectProvider<SlipOcrPublisher> publisherProvider;
    @Mock
    private SlipOcrPublisher publisher;

    @InjectMocks
    private SlipService slipService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId(UUID.randomUUID());
    }

    private Slip queuedSlip(long id) {
        return Slip.builder().id(id).userId(user.getUserId()).imagePath("2026/11/05/abc.png").status(SlipService.STATUS_QUEUED).build();
    }

    private OcrService.OcrResponse ocrSuccess() {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("bank", "KBank");
        data.put("receiver", "Shop A");
        data.put("amount", "250.50");
        OcrService.OcrResponse response = new OcrService.OcrResponse();
        response.setFilename("abc.png");
        response.setStatus("success");
        response.setData(data);
        return response;
    }

    // ---------- enqueue ----------

    @Test
    void enqueue_savesQueuedSlipAndPublishesAfterTheSlipIsPersisted() throws Exception {
        when(publisherProvider.getIfAvailable()).thenReturn(publisher);
        when(minioService.uploadFile(any())).thenReturn("2026/11/05/abc.png");
        when(slipRepository.save(any(Slip.class))).thenAnswer(inv -> {
            Slip s = inv.getArgument(0);
            if (s.getId() == null) s.setId(42L);
            return s;
        });
        MockMultipartFile file = new MockMultipartFile("files", "slip.png", "image/png", new byte[]{1, 2, 3});

        List<Slip> result = slipService.enqueueSlips(List.of(file), user);

        assertEquals(1, result.size());
        assertEquals(SlipService.STATUS_QUEUED, result.get(0).getStatus());
        ArgumentCaptor<SlipOcrMessage> message = ArgumentCaptor.forClass(SlipOcrMessage.class);
        var order = inOrder(slipRepository, publisher);
        order.verify(slipRepository).save(any(Slip.class));
        order.verify(publisher).publish(message.capture());
        assertEquals(42L, message.getValue().slipId());
        assertEquals(user.getUserId(), message.getValue().userId());
    }

    @Test
    void enqueue_marksTheSlipFailedWhenTheBrokerIsUnreachable() throws Exception {
        when(publisherProvider.getIfAvailable()).thenReturn(publisher);
        when(minioService.uploadFile(any())).thenReturn("2026/11/05/abc.png");
        when(slipRepository.save(any(Slip.class))).thenAnswer(inv -> {
            Slip s = inv.getArgument(0);
            if (s.getId() == null) s.setId(7L);
            return s;
        });
        doThrow(new RuntimeException("broker down")).when(publisher).publish(any());
        MockMultipartFile file = new MockMultipartFile("files", "slip.png", "image/png", new byte[]{1});

        List<Slip> result = slipService.enqueueSlips(List.of(file), user);

        assertEquals(SlipService.STATUS_FAILED, result.get(0).getStatus());
    }

    @Test
    void enqueue_isRejectedWhenAsyncProcessingIsDisabled() {
        when(publisherProvider.getIfAvailable()).thenReturn(null);
        MockMultipartFile file = new MockMultipartFile("files", "slip.png", "image/png", new byte[]{1});

        BusinessException ex = assertThrows(BusinessException.class, () -> slipService.enqueueSlips(List.of(file), user));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        verifyNoInteractions(minioService);
    }

    // ---------- consume ----------

    @Test
    void process_fillsTheSlipFromTheOcrResultAndMarksItProcessed() {
        Slip slip = queuedSlip(1L);
        when(slipRepository.findById(1L)).thenReturn(Optional.of(slip));
        when(minioService.getFile(slip.getImagePath())).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(ocrService.verifySlips(any())).thenReturn(List.of(ocrSuccess()));
        when(receiverMappingService.suggestMapping(user.getUserId(), "Shop A")).thenReturn(Optional.empty());

        slipService.processQueuedSlip(1L);

        assertEquals(SlipService.STATUS_PROCESSED, slip.getStatus());
        assertEquals("KBank", slip.getSenderBank());
        assertEquals("Shop A", slip.getReceiverName());
        assertEquals(0, new java.math.BigDecimal("250.50").compareTo(slip.getAmount()));
        verify(slipRepository).save(slip);
    }

    @Test
    void process_isIdempotent_aRedeliveredMessageForAHandledSlipIsSkipped() {
        Slip slip = queuedSlip(2L);
        slip.setStatus(SlipService.STATUS_PROCESSED);
        when(slipRepository.findById(2L)).thenReturn(Optional.of(slip));

        slipService.processQueuedSlip(2L);

        verifyNoInteractions(ocrService, minioService);
        verify(slipRepository, never()).save(any());
    }

    @Test
    void process_dropsTheMessageWhenTheSlipWasDeleted() {
        when(slipRepository.findById(3L)).thenReturn(Optional.empty());

        slipService.processQueuedSlip(3L);

        verifyNoInteractions(ocrService, minioService);
    }

    @Test
    void process_throwsWhenOcrFailsSoTheRetryAndDeadLetterQueueTakeOver() {
        Slip slip = queuedSlip(4L);
        when(slipRepository.findById(4L)).thenReturn(Optional.of(slip));
        when(minioService.getFile(slip.getImagePath())).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        OcrService.OcrResponse failed = new OcrService.OcrResponse();
        failed.setStatus("error");
        when(ocrService.verifySlips(any())).thenReturn(List.of(failed));

        assertThrows(BusinessException.class, () -> slipService.processQueuedSlip(4L));

        assertEquals(SlipService.STATUS_QUEUED, slip.getStatus());   // untouched, so a retry can still succeed
        verify(slipRepository, never()).save(any());
    }

    @Test
    void process_appliesTheReceiverMappingLikeTheSynchronousFlow() {
        Slip slip = queuedSlip(5L);
        when(slipRepository.findById(5L)).thenReturn(Optional.of(slip));
        when(minioService.getFile(slip.getImagePath())).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        when(ocrService.verifySlips(any())).thenReturn(List.of(ocrSuccess()));
        UUID debtId = UUID.randomUUID();
        ReceiverMapping mapping = ReceiverMapping.builder().debtId(debtId).build();
        when(receiverMappingService.suggestMapping(user.getUserId(), "Shop A")).thenReturn(Optional.of(mapping));
        com.example.capstone.entity.Debt debt = new com.example.capstone.entity.Debt();
        when(debtRepository.findByDebtIdAndUserId(debtId, user.getUserId())).thenReturn(Optional.of(debt));

        slipService.processQueuedSlip(5L);

        verify(repaymentService).processDebtPayment(eq(debt), any(), any(), eq(5L));
    }

    // ---------- dead letter ----------

    @Test
    void markFailed_onlyChangesSlipsThatAreStillQueued() {
        Slip queued = queuedSlip(6L);
        Slip done = queuedSlip(7L);
        done.setStatus(SlipService.STATUS_PROCESSED);
        when(slipRepository.findById(6L)).thenReturn(Optional.of(queued));
        when(slipRepository.findById(7L)).thenReturn(Optional.of(done));

        slipService.markFailed(6L);
        slipService.markFailed(7L);

        assertEquals(SlipService.STATUS_FAILED, queued.getStatus());
        assertEquals(SlipService.STATUS_PROCESSED, done.getStatus());
    }

    // ---------- listener ----------

    @Test
    void listener_delegatesBothQueuesToTheService() {
        SlipService service = mock(SlipService.class);
        SlipOcrListener listener = new SlipOcrListener(service);
        SlipOcrMessage message = new SlipOcrMessage(9L, user.getUserId(), "a.png", "image/png");

        listener.onSlipQueued(message);
        listener.onSlipDead(message);

        verify(service).processQueuedSlip(9L);
        verify(service).markFailed(9L);
    }
}
