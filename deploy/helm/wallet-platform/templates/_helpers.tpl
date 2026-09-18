{{/*
Expand the name of the chart.
*/}}
{{- define "wallet-platform.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "wallet-platform.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "wallet-platform.labels" -}}
helm.sh/chart: {{ include "wallet-platform.name" . }}-{{ .Chart.Version | replace "+" "_" }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: wallet-platform
{{- end }}

{{/*
Edge selector labels
*/}}
{{- define "wallet-platform.edgeSelectorLabels" -}}
app.kubernetes.io/name: {{ include "wallet-platform.name" . }}-edge
app.kubernetes.io/instance: {{ .Release.Name }}
tier: edge
{{- end }}

{{/*
Core selector labels
*/}}
{{- define "wallet-platform.coreSelectorLabels" -}}
app.kubernetes.io/name: {{ include "wallet-platform.name" . }}-core
app.kubernetes.io/instance: {{ .Release.Name }}
tier: core
{{- end }}
