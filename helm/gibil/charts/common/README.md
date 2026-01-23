# common

![Version: 1.21.1](https://img.shields.io/badge/Version-1.21.1-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 0.0.1](https://img.shields.io/badge/AppVersion-0.0.1-informational?style=flat-square)

A Helm chart for Entur's Kubernetes workloads

## Highlighted features

* Defaults typically match a properly configured Spring Boot project
* Automatic HA with HPA and PDB in `prd`
* Enforces explicit setting of important aspecs such as traffic type
* Rule based safety net, a chart that breaks business rules will fail with a helpful message
* Convention based automatic limit configuration. Cpu is 5x request, and memory is +20%.

## Take full control

* Most properties can be overriden to your specific needs.
* Read the values.yaml file to get template documentation.
### Fully customize `container.probes.spec` and `hpa.spec` with literal Kubernetes configuration
<details>

  <summary>Example container.probes.spec</summary>

```yaml
common:
  container:
    probes:
      enabled: true
      spec:
        startupProbe:
          tcpSocket:
            port: 3000
          periodSeconds: 1
          timeoutSeconds: 1
          failureThreshold: 300
        livenessProbe: ...
        readinessProbe: ...
```
</details>
<details>

  <summary>Example hpa.spec</summary>

```yaml
common:
  hpa:
    spec:
      metrics:
        - type: Resource
          resource:
            name: cpu
            target:
              type: Utilization
              averageUtilization: 60
      behavior:
        scaleUp:
          stabilizationWindowSeconds: 60
          policies:
            - type: Pods
              value: 1
              periodSeconds: 60
      maxReplicas: 7
      minReplicas: 2

```
</details>

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| app | string | `nil` | Application name, typically on the form `the-application` |
| configmap.data | object | `{}` | Set data for configmap |
| configmap.enabled | bool | false | Enable or disable the configmap |
| container.args | string | `nil` | Optionally set the arguments that will be passed to the command, e.g. ["arg1","arg2"]. |
| container.command | string | `nil` | Optionally set the command that will run in the pod. If not set, the entrypoint for the container-image is used (recommended for most Java-apps). |
| container.cpu | float | 0.1 | Set CPU without any unit. 100m is 0.1 |
| container.cpuLimit | float | `5 x cpu` | Set CPU limit without any unit. 100m is 0.1 |
| container.env | list | `[]` | Specify `env` entries for your container |
| container.envFrom | list | `[]` | Attach secrets and configmaps to your `env` |
| container.forceReplicas | int | `nil` | Force replicas disables autoscaling and PDB, if set to 1 it will use Recreate strategy |
| container.labels | object | `{}` | Add labels to your pods |
| container.lifecycle | object | `{}` | Set pod lifecycle handlers |
| container.maxReplicas | int | `nil` | Set the maxReplicas for your HPA |
| container.memory | int | 16 | Set memory without any unit, `Mi` is inferred |
| container.memoryLimit | string | `1.2 * memory` | Set memory limit without any unit, `Mi` is inferred |
| container.minAvailable | string | 50% | Set the minimal available replicas, used by PDB |
| container.name | string | .app | Name of container |
| container.probes.enabled | bool | `true` | Enable or disable probes |
| container.probes.liveness.failureThreshold | int | 6 | Set the failure threshold |
| container.probes.liveness.grpc | string | `nil` | Specify grpc probes for a port. Needs `port` child stanza |
| container.probes.liveness.initialDelaySeconds | int | `0` | Set the initial delay for the probe |
| container.probes.liveness.path | string | /actuator/health/liveness | Set the path for liveness probe |
| container.probes.liveness.periodSeconds | int | 5 | Set the period of checking |
| container.probes.liveness.successThreshold | int | 1 | Set the success threshold |
| container.probes.readiness.failureThreshold | int | 6 | Set the failure threshold |
| container.probes.readiness.grpc | string | `nil` | Specify grpc probes for a port. Needs `port` child stanza |
| container.probes.readiness.initialDelaySeconds | int | `0` | Set the initial delay for the probe |
| container.probes.readiness.path | string | /actuator/health/readiness | Set the path for liveness probe |
| container.probes.readiness.periodSeconds | int | 5 | Set the period of checking |
| container.probes.readiness.successThreshold | int | 1 | Set the success threshold |
| container.probes.spec | string | `nil` | Override with k8s spec for custom probes |
| container.probes.startup.failureThreshold | int | 300 | Set the failure threshold |
| container.probes.startup.grpc | string | `nil` | Specify grpc probes for a port. Needs `port` child stanza |
| container.probes.startup.periodSeconds | int | 1 | Set the period of checking |
| container.prometheus.enabled | bool | `false` | Enable or disable Prometheus |
| container.prometheus.path | string | /actuator/prometheus | Set the path for scraping metrics |
| container.prometheus.port | int | service.internalPort | Set the port for prometheus scraping |
| container.replicas | int | `nil` | Set the target replica count, if equal to 1 the PDB minAvailable will be set to 100% |
| container.terminationGracePeriodSeconds | int | `nil` | Override pod terminationGracePeriodSeconds (default 30s). |
| container.uid | int | 1000 | Set the uid that your user runs with |
| container.volumeMounts | list | `[]` | Configure volume mounts, accepts kubernetes syntax |
| container.volumes | list | `[]` | Configure volume, accepts kubernetes syntax |
| containers | list | `[]` | Takes a list of `container` entries, you must add a `name` field for each entry |
| cron.activeDeadlineSeconds | int | `nil` | Active deadline seconds for the job, default 24 hours (86300s) |
| cron.concurrencyPolicy | string | Forbid | Concurrency policy |
| cron.enabled | bool | `false` | Enable or disable the cron job |
| cron.failedJobsHistoryLimit | int | 1 | Failed jobs history limit |
| cron.labels | object | `{}` | Add labels to your pods |
| cron.restartPolicy | string | OnFailure | Override pod restartPolicy (default OnFailure). |
| cron.schedule | string | `nil` | Required crontab schedule `* * * * * *` |
| cron.serviceAccountName | string | application | Override pod serviceAccountName (default application). |
| cron.successfulJobsHistoryLimit | int | 1 | Successful jobs history limit |
| cron.suspend | string | false | Suspend flag |
| cron.terminationGracePeriodSeconds | int | false | Override pod terminationGracePeriodSeconds (default 30s). |
| cron.volumes | list | `[]` | Configure volume, accepts kubernetes syntax |
| deployment.enabled | bool | `true` | Enable or disable the deployment |
| deployment.forceReplicas | int | `nil` | Force replicas disables autoscaling and PDB, if set to 1 it will use Recreate strategy |
| deployment.labels | object | `{}` | Add labels to your pods |
| deployment.maxReplicas | string | 10 | Set the max replica count |
| deployment.maxSurge | string | 25% | Limit max surge for rolling updates (default 25%). Not in use when using forceReplicas. |
| deployment.maxUnavailable | string | 25% | Limit max unavailable for rolling updates (default 25%). Not in use when using forceReplicas. |
| deployment.minAvailable | string | 50% | Set minimum available % |
| deployment.minReadySeconds | int | 0 | See https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#progress-deadline-seconds |
| deployment.replicas | string | container.replicas | Set the target replica count |
| deployment.serviceAccountName | string | application | Override pod serviceAccountName (default application). |
| deployment.terminationGracePeriodSeconds | int | `nil` | Override pod terminationGracePeriodSeconds (default 30s). |
| deployment.volumes | list | `[]` | Configure volume, accepts kubernetes syntax |
| env | string | `nil` | The current env, override in your `values-kub-ent-$env.yaml` files to `dev`, `tst` or `prd` |
| grpc | bool | `false` | Enable gRPC which will add an annotation and use grpc probes |
| hpa.spec | object | `{}` | Custom spec for HPA, inherits `scaleTargetRef` and min/max replicas. ps: Reason why we have set 100% cpu as default is because the java applications are resource hogs during startup.     If you have good startupProbe/readinessProbes in place you can lower the cpu average utilization to ie 50/60%.     - Or scale on other (custom) metrics. |
| ingress.annotations | object | `{}` | Optionally set annotations for the ingress |
| ingress.enabled | bool | `true` | Enable or disable the ingress |
| ingress.host | string | `nil` | Set the host name, do this in your `values-kub-ent-$env.yaml` files |
| ingress.trafficType | string | `nil` | Set the traffic type (`api`,`public` or `http2` for gRPC) |
| ingresses | list | `[]` | Specify a list of `ingress` specs |
| initContainers | list | `[]` | See: https://kubernetes.io/docs/concepts/workloads/pods/init-containers/ |
| labels | object | `{ app shortname team common:version environment }` | Specify additional labels for every resource |
| pdb.minAvailable | string | 50% | Set minimum available %, this overrides pdb setting minAvailable in deployment/container |
| postgres.connectionConfig | string | `nil` | Override name for connection configmap. This must at least contain `INSTANCES`. |
| postgres.cpu | float | 0.05 | Configure cpu request for proxy |
| postgres.cpuLimit | float | `nil` | Configure optional cpu limit for proxy |
| postgres.credentialsSecret | string | `nil` | Override name for credentials secret. This must at least contain `PGUSER` and `PGPASSWORD`. |
| postgres.enabled | bool | false | Enable or disable the proxy |
| postgres.memory | int | 16 | Configure memory request for proxy without units, `Mi` inferred |
| postgres.memoryLimit | int | 16 | Configure memoryLimit for proxy without units, `Mi` inferred |
| releaseName | string | `nil` | Override release name, useful for multiple deployments |
| secrets | object | `{}` | Add externalSecret to sync secrets from secret manager |
| service.annotations | object | `{}` | Optionally set annotations for the service |
| service.enabled | bool | `true` | Enable or disable the service |
| service.externalPort | int | 80 | Set the external port for your service |
| service.internalPort | int | 8080 | Set the internal port for your service |
| shortname | string | `nil` | `id` for GCP 2.0, typically on the form `theapp`. Max 10 characters |
| team | string | `nil` | Your team name, without a `team-` prefix |
| vpa.enabled | bool | `true` | Enable Vertical Pod Autoscaler to get resource requirement and limit recommendations |

