{{- define "financecare.name" -}}
{{- .Chart.Name -}}
{{- end -}}

{{- define "financecare.fullname" -}}
{{- if contains .Chart.Name .Release.Name -}}
{{- .Release.Name | trunc 50 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 50 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "financecare.labels" -}}
app.kubernetes.io/name: {{ include "financecare.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}

{{- define "financecare.selectorLabels" -}}
app.kubernetes.io/name: {{ include "financecare.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: backend
{{- end -}}

{{/* Name of the Secret the pods read their credentials from. */}}
{{- define "financecare.secretName" -}}
{{- if .Values.existingSecret -}}
{{- .Values.existingSecret -}}
{{- else -}}
{{- printf "%s-secrets" (include "financecare.fullname" .) -}}
{{- end -}}
{{- end -}}

{{- define "financecare.postgresHost" -}}
{{- printf "%s-postgres" (include "financecare.fullname" .) -}}
{{- end -}}

{{- define "financecare.redisHost" -}}
{{- printf "%s-redis" (include "financecare.fullname" .) -}}
{{- end -}}
