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
    - name: "redeploy-extimeData-fetch"
      image: curlimages/curl:latest
      imagePullPolicy: IfNotPresent
      command:
        - sh
        - -c
        - |
          curl -o /tmp/rb_avi-aggregated-netex.zip {{ .Values.common.init.extimeData }}
          unzip /tmp/rb_avi-aggregated-netex.zip -d /app/extimeData
          rm /tmp/rb_avi-aggregated-netex.zip
          ls -la /app/extimeData/
      volumeMounts:
        - name: extime-data
          mountPath: /app/extimeData
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