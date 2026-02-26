{{/* vim: set filetype=mustache: */}}
{{/*
Expand the name of the chart.
*/}}
{{- define "app.name" -}}
gibil
{{- end -}}

{{- define "app.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/* Generate basic labels */}}
{{- define "gibil.common.labels" }}
app: {{ template "app.name" . }}
chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
release: {{ .Release.Name }}
team: ror
namespace: {{ .Release.Namespace }}
{{- end }}

{{- define "gibil.cron-job-template" }}
spec:
  containers:
    - name: "redeploy-{{ template "app.name" . }}"
      image: eu.gcr.io/entur-system-1287/deployment-rollout-restart:0.1.12
      imagePullPolicy: IfNotPresent
      command:
        - ./redeploy_generic_deployment.sh
      env:
        - name: DEPLOYMENT_NAME
          value: {{ template "app.name" . }}
        - name: DEPLOYMENT_NAMESPACE
          value: {{ .Release.Namespace }}
      securityContext:
        runAsNonRoot: true
        allowPrivilegeEscalation: false
        capabilities:
          drop: ["ALL"]
        seccompProfile:
          type: RuntimeDefault
      resources: {}
  dnsPolicy: ClusterFirst
  restartPolicy: Never
  schedulerName: default-scheduler
  securityContext:
    runAsGroup: 1000
    runAsNonRoot: true
    runAsUser: 1000
  serviceAccountName: application
  terminationGracePeriodSeconds: 30
{{- end }}