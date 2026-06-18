{{- define "agent-panel.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "agent-panel.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- printf "%s" $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{- define "agent-panel.namespace" -}}
{{- .Values.namespace.name }}
{{- end }}

{{- define "agent-panel.labels" -}}
app.kubernetes.io/name: {{ include "agent-panel.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ include "agent-panel.name" . }}-{{ .Chart.Version }}
{{- end }}

{{- define "agent-panel.selectorLabels" -}}
app.kubernetes.io/name: {{ include "agent-panel.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
