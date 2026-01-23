# Changelog

## [1.21.1](https://github.com/entur/helm-charts/compare/common-v1.21.0...common-v1.21.1) (2025-11-24)


### Bug Fixes

* switch to use v1 of external-secrets🎉 ([#230](https://github.com/entur/helm-charts/issues/230)) ([f798b3d](https://github.com/entur/helm-charts/commit/f798b3ddbe7d0164994b337a940216e154408a0b))

## [1.21.0](https://github.com/entur/helm-charts/compare/common-v1.20.3...common-v1.21.0) (2025-11-24)


### Features

* set Active deadline seconds for cronjob (fixes [#223](https://github.com/entur/helm-charts/issues/223)) ([#226](https://github.com/entur/helm-charts/issues/226)) ([599bbfe](https://github.com/entur/helm-charts/commit/599bbfe3e620d0a3c641a9317ff5e323bf3b6092))


### Bug Fixes

* allow manual run to bump versions ([#214](https://github.com/entur/helm-charts/issues/214)) ([0b1bbe9](https://github.com/entur/helm-charts/commit/0b1bbe97278ea81ce36bfdc476fc6f13c7e53183))

## [1.20.3](https://github.com/entur/helm-charts/compare/common-v1.20.2...common-v1.20.3) (2025-03-18)


### Bug Fixes

* Refer to readme files in release-please so versions are bumped ([ac8a4a9](https://github.com/entur/helm-charts/commit/ac8a4a99aeff07311f72f3cd8ebc6a27935a3ede))
* Run helm-docs automatically after release ([ac8a4a9](https://github.com/entur/helm-charts/commit/ac8a4a99aeff07311f72f3cd8ebc6a27935a3ede))

## [1.20.2](https://github.com/entur/helm-charts/compare/common-v1.20.1...common-v1.20.2) (2025-03-17)


### Bug Fixes

* add initContainers ([#205](https://github.com/entur/helm-charts/issues/205)) ([a67ad0c](https://github.com/entur/helm-charts/commit/a67ad0c128218ae7343ceba0d7b63caf92375189))
* inherit prometheus path default from container ([#201](https://github.com/entur/helm-charts/issues/201)) ([49e04b4](https://github.com/entur/helm-charts/commit/49e04b45f6848369b50c251be33f804e5d68fe91))

## [1.20.1](https://github.com/entur/helm-charts/compare/common-v1.20.0...common-v1.20.1) (2025-02-21)


### Bug Fixes

* labels on cronjob pods and schedule fix ([#196](https://github.com/entur/helm-charts/issues/196)) ([186796d](https://github.com/entur/helm-charts/commit/186796d1b78ee421784da3b42a242df7ea88212b))

## [1.20.0](https://github.com/entur/helm-charts/compare/common-v1.19.0...common-v1.20.0) (2024-08-23)


### Features

* add support for disabling VPA ([#188](https://github.com/entur/helm-charts/issues/188)) ([8ebafd7](https://github.com/entur/helm-charts/commit/8ebafd7cb54c05cc0f3e4ab2ada04eb85fa309f5))

## [1.19.0](https://github.com/entur/helm-charts/compare/common-v1.18.6...common-v1.19.0) (2024-05-27)


### Features

* annotations and pdb fix ([#175](https://github.com/entur/helm-charts/issues/175)) ([c1b027d](https://github.com/entur/helm-charts/commit/c1b027d5aa295c1c1de489143a864be2b5f1cce6))

## [1.18.6](https://github.com/entur/helm-charts/compare/common-v1.18.5...common-v1.18.6) (2024-05-23)

### Features

- move psql container first ([#176](https://github.com/entur/helm-charts/issues/176)) ([5184eac](https://github.com/entur/helm-charts/commit/5184eac3b63d183e7c78d875ac67e545e4c69b2e))

## 1.18.5 [YANKED]

## [1.18.4](https://github.com/entur/helm-charts/compare/common-v1.18.3...common-v1.18.4) (2024-03-07)

### Bug Fixes

- add deployment.minReadySeconds ([#171](https://github.com/entur/helm-charts/issues/171)) ([0543d78](https://github.com/entur/helm-charts/commit/0543d786314110fe8041af472cbea12378b7cd7b))

## [1.18.3](https://github.com/entur/helm-charts/compare/common-v1.18.2...common-v1.18.3) (2024-02-29)

### Bug Fixes

- only create one psql sidecar ([#164](https://github.com/entur/helm-charts/issues/164)) ([042fc6a](https://github.com/entur/helm-charts/commit/042fc6a3b1b80089531d9761dc5cc0c6a78e9693))

## [1.18.2](https://github.com/entur/helm-charts/compare/common-v1.18.1...common-v1.18.2) (2024-02-15)

### Bug Fixes

- update sqlproxy image from USA to EU ([#160](https://github.com/entur/helm-charts/issues/160)) ([a88bbf9](https://github.com/entur/helm-charts/commit/a88bbf99fd80371cf1c92b4c91829247d745c97b))

## [1.18.1](https://github.com/entur/helm-charts/compare/common-v1.18.0...common-v1.18.1) (2024-02-09)

### Bug Fixes

- added support for external secrets to cron templates ([#157](https://github.com/entur/helm-charts/issues/157)) ([4e6cd6e](https://github.com/entur/helm-charts/commit/4e6cd6e56062de418de8e8b89ef8acb83c6e7914))

## [1.18.0](https://github.com/entur/helm-charts/compare/common-v1.17.5...common-v1.18.0) (2024-01-25)

### Features

- COMMON_ENV release ([#151](https://github.com/entur/helm-charts/issues/151)) + check if .hpa.spec.minReplicas is set ([#154](https://github.com/entur/helm-charts/issues/154)) ([82feb66](https://github.com/entur/helm-charts/commit/82feb66b4abba517560fe33e34df17654b6e99f2))
- define runtime environment as common env ([#151](https://github.com/entur/helm-charts/issues/151)) ([c692a59](https://github.com/entur/helm-charts/commit/c692a5928dc362ae16f04e4b1af46166fd5ce71f))

## [1.17.5](https://github.com/entur/helm-charts/compare/common-v1.17.4...common-v1.17.5) (2024-01-03)

### Bug Fixes

- no default replicas ([#149](https://github.com/entur/helm-charts/issues/149)) ([a0726dd](https://github.com/entur/helm-charts/commit/a0726dd1941aebe3e57b363c41a21755bf0d4bf7))

## [1.17.4](https://github.com/entur/helm-charts/compare/common-v1.17.3...common-v1.17.4) (2023-12-05)

### Bug Fixes

- do not set replicas if HPA is enabled ([#147](https://github.com/entur/helm-charts/issues/147)) ([200da98](https://github.com/entur/helm-charts/commit/200da98458a57975bad30e787f124fef22173452))

## [1.17.3](https://github.com/entur/helm-charts/compare/common-v1.17.2...common-v1.17.3) (2023-10-30)

### Bug Fixes

- override store name ([#143](https://github.com/entur/helm-charts/issues/143)) ([114dad7](https://github.com/entur/helm-charts/commit/114dad710f9634a0e0120f6380e892c64c1fc592))

## [1.17.2](https://github.com/entur/helm-charts/compare/common-v1.17.1...common-v1.17.2) (2023-10-25)

### Bug Fixes

- doc update [skip ci] ([#128](https://github.com/entur/helm-charts/issues/128)) ([a66a7ed](https://github.com/entur/helm-charts/commit/a66a7eda637c92517f933b65fa4ca566f6a8cb0b))
- invalidate ext secret each deploy ([#138](https://github.com/entur/helm-charts/issues/138)) ([e268b95](https://github.com/entur/helm-charts/commit/e268b95104d851cfc83dfa1dd537714e6559e4c7))

## [1.17.1](https://github.com/entur/helm-charts/compare/common-v1.17.0...common-v1.17.1) (2023-09-07)

### Bug Fixes

- clear env if not used, cleanup tests ([#124](https://github.com/entur/helm-charts/issues/124)) ([a1af901](https://github.com/entur/helm-charts/commit/a1af90120d1707be5c760cdfdf2474834ba471e5))

## [1.17.0](https://github.com/entur/helm-charts/compare/common-v1.16.2...common-v1.17.0) (2023-08-28)

### Features

- add ephemeral-storage ([#120](https://github.com/entur/helm-charts/issues/120)) ([a81b2c4](https://github.com/entur/helm-charts/commit/a81b2c46d3419bddb445a85b12382e495e2032fb))

## [1.16.2](https://github.com/entur/helm-charts/compare/common-v1.16.1...common-v1.16.2) (2023-08-14)

### Bug Fixes

- new release ([#118](https://github.com/entur/helm-charts/issues/118)) ([74864a0](https://github.com/entur/helm-charts/commit/74864a0e3eeb0fabf430b2e9b708bda824186e0b))

## [1.16.1](https://github.com/entur/helm-charts/compare/common-v1.16.0...common-v1.16.1) (2023-08-14)

### Bug Fixes

- version fix ([#116](https://github.com/entur/helm-charts/issues/116)) ([f36774a](https://github.com/entur/helm-charts/commit/f36774a80302c03ca408f4fe0dbbf3b1f0ff2b0e))

## [1.15.0](https://github.com/entur/helm-charts/compare/common-v1.14.0...common-v1.15.0) (2023-06-29)

### Features

- add pdb for sbx/dev/test envs + logic ([#109](https://github.com/entur/helm-charts/issues/109)) ([b4b246a](https://github.com/entur/helm-charts/commit/b4b246af0eeacc9d15f3702e01b303d919b900d3))

## [1.14.0](https://github.com/entur/helm-charts/compare/common-v1.13.1...common-v1.14.0) (2023-06-27)

### Features

- add support for external-secrets ([#112](https://github.com/entur/helm-charts/issues/112)) ([e913ea1](https://github.com/entur/helm-charts/commit/e913ea1246496b6523862d622acc28915f7c032f))

## [1.13.1](https://github.com/entur/helm-charts/compare/common-v1.13.0...common-v1.13.1) (2023-06-27)

### Bug Fixes

- allow no `limits.cpu` for postgres ([#110](https://github.com/entur/helm-charts/issues/110)) ([4c8cb96](https://github.com/entur/helm-charts/commit/4c8cb9624ef63e86636486b28e33c91ec28633c9))

## [1.13.0](https://github.com/entur/helm-charts/compare/common-v1.12.1...common-v1.13.0) (2023-06-20)

### Features

- vpa enabled by default in audit mode ([#107](https://github.com/entur/helm-charts/issues/107)) ([cc3dcad](https://github.com/entur/helm-charts/commit/cc3dcad577ca53c6af6621bd7cc7d48813bd287e))

## [1.12.1](https://github.com/entur/helm-charts/compare/common-v1.12.0...common-v1.12.1) (2023-06-19)

### Bug Fixes

- allow no `limits.cpu` ([#105](https://github.com/entur/helm-charts/issues/105)) ([9988a31](https://github.com/entur/helm-charts/commit/9988a31d4ca73ff5537cd51527e5951c6d9c3be3))

## [1.12.0](https://github.com/entur/helm-charts/compare/common-v1.11.1...common-v1.12.0) (2023-05-26)

### Features

- ability to use grpc probes from k8s 1.24 ([#99](https://github.com/entur/helm-charts/issues/99)) ([3816c8c](https://github.com/entur/helm-charts/commit/3816c8c2fa126870dd48eedfe5a7f87b1cc95be6))

## [1.11.1](https://github.com/entur/helm-charts/compare/common-v1.11.0...common-v1.11.1) (2023-05-22)

### Bug Fixes

- set fsGroup ([#97](https://github.com/entur/helm-charts/issues/97)) ([d8f7c57](https://github.com/entur/helm-charts/commit/d8f7c5780174521d52091edacd38322f12c0403f))

## [1.11.0](https://github.com/entur/helm-charts/compare/common-v1.10.0...common-v1.11.0) (2023-03-21)

### Features

- option to add custom annotations for ingress ([#93](https://github.com/entur/helm-charts/issues/93)) ([9eed6a5](https://github.com/entur/helm-charts/commit/9eed6a548aeb358d574c8c6aff0b4eed5efdf706))

## [1.10.0](https://github.com/entur/helm-charts/compare/common-v1.9.0...common-v1.10.0) (2023-03-06)

### Features

- ability to override probes with probes.spec using k8s yaml ([#90](https://github.com/entur/helm-charts/issues/90)) ([af2394a](https://github.com/entur/helm-charts/commit/af2394a81828c32c5d18e2a8322c07b6262202d9))

## [1.9.0](https://github.com/entur/helm-charts/compare/common-v1.8.5...common-v1.9.0) (2023-02-24)

### Features

- Cloud sql proxy update and memoryLimit option ([#88](https://github.com/entur/helm-charts/issues/88)) ([b1ce15f](https://github.com/entur/helm-charts/commit/b1ce15fadd066637dcc11561a70283c87241f8f5))

## [1.8.5](https://github.com/entur/helm-charts/compare/common-v1.8.4...common-v1.8.5) (2023-02-20)

### Bug Fixes

- not always enable hpa ([#86](https://github.com/entur/helm-charts/issues/86)) ([cdc9997](https://github.com/entur/helm-charts/commit/cdc9997114c2d4b1c099c20c8c1a26aae0faa80f))

## [1.8.4](https://github.com/entur/helm-charts/compare/common-v1.8.3...common-v1.8.4) (2023-02-20)

### Bug Fixes

- use maxReplicas, forceReplicas, minAvailable from deployment if set there ([#83](https://github.com/entur/helm-charts/issues/83)) ([d077a12](https://github.com/entur/helm-charts/commit/d077a12ee2753e99b86be078db4b39d2a7d1e812))

## [1.8.3](https://github.com/entur/helm-charts/compare/common-v1.8.2...common-v1.8.3) (2023-02-20)

### Bug Fixes

- add annotations to service, needed for internal-http2 ([#80](https://github.com/entur/helm-charts/issues/80)) ([2c56751](https://github.com/entur/helm-charts/commit/2c567513c273d5d5e4d971ae781c71eb6043a7d2))

## [1.8.2](https://github.com/entur/helm-charts/compare/common-v1.8.1...common-v1.8.2) (2023-02-09)

### Bug Fixes

- container.probes.enabled flag ([#78](https://github.com/entur/helm-charts/issues/78)) ([ffc313b](https://github.com/entur/helm-charts/commit/ffc313b141630cf417ef8f7b542b500ce523c0ac))

## [1.8.1](https://github.com/entur/helm-charts/compare/common-v1.8.0...common-v1.8.1) (2023-01-11)

### Bug Fixes

- set cpuLimit 0.3 for sql sidecar ([#57](https://github.com/entur/helm-charts/issues/57)) ([2858deb](https://github.com/entur/helm-charts/commit/2858deb8f7c0475d1c1f91015747c1e2044241dd))

## [1.8.0](https://github.com/entur/helm-charts/compare/common-v1.7.0...common-v1.8.0) (2023-01-11)

### Features

- enable releaseName override ([#72](https://github.com/entur/helm-charts/issues/72)) ([31930d0](https://github.com/entur/helm-charts/commit/31930d0aa5d02da35a5e3becb3151eb161a473d6))

## [1.7.0](https://github.com/entur/helm-charts/compare/common-v1.6.0...common-v1.7.0) (2023-01-09)

### Features

- Make serviceAccountName overridable for Deployment and CronJob resources ([#70](https://github.com/entur/helm-charts/issues/70)) ([61b7f11](https://github.com/entur/helm-charts/commit/61b7f11e8086d34cad961b6f40b7447739cebfad))

## [1.6.0](https://github.com/entur/helm-charts/compare/common-v1.5.0...common-v1.6.0) (2022-12-20)

### Features

- Add support for setting maxUnavailable and maxSurge for rolling updates ([#66](https://github.com/entur/helm-charts/issues/66)) ([47c0cec](https://github.com/entur/helm-charts/commit/47c0cece9e95f08184f08ef8c0fe21ff4d3b2426))

## [1.5.0](https://github.com/entur/helm-charts/compare/common-v1.4.2...common-v1.5.0) (2022-12-19)

### Features

- Expose probe properties initialDelaySeconds and successThreshold and container lifecycle. ([#59](https://github.com/entur/helm-charts/issues/59)) ([5b26947](https://github.com/entur/helm-charts/commit/5b26947438c7a55063413c970f3c378b4a3b255f))

## [1.4.2](https://github.com/entur/helm-charts/compare/common-v1.4.1...common-v1.4.2) (2022-12-16)

### Bug Fixes

- Add missing template block in CronJob template ([#56](https://github.com/entur/helm-charts/issues/56)) ([0ed3b82](https://github.com/entur/helm-charts/commit/0ed3b828c8c9d452d0032c3e67d7edda92d676f1))

## [1.4.1](https://github.com/entur/helm-charts/compare/common-v1.4.0...common-v1.4.1) (2022-12-09)

### Bug Fixes

- apply cron fix ([#54](https://github.com/entur/helm-charts/issues/54)) ([5836709](https://github.com/entur/helm-charts/commit/58367097d5558281172f987765c072a9d7696227))

## [1.4.0](https://github.com/entur/helm-charts/compare/common-v1.3.3...common-v1.4.0) (2022-12-09)

### Features

- Add support for running command with arguments in containers ([#51](https://github.com/entur/helm-charts/issues/51)) ([8479bed](https://github.com/entur/helm-charts/commit/8479bed8eed5d660545591ea510d889bb69edd75))

## [1.3.3](https://github.com/entur/helm-charts/compare/common-v1.3.2...common-v1.3.3) (2022-12-07)

### Bug Fixes

- allow override of postgres credentials and connection ([#49](https://github.com/entur/helm-charts/issues/49)) ([ef0b05b](https://github.com/entur/helm-charts/commit/ef0b05b49fed7ea48b8e1d5e31bc5e350e81c102))

## [1.3.2](https://github.com/entur/helm-charts/compare/common-v1.3.1...common-v1.3.2) (2022-12-06)

### Bug Fixes

- configmapRef should use release-name ([#46](https://github.com/entur/helm-charts/issues/46)) ([eb711bd](https://github.com/entur/helm-charts/commit/eb711bd1ba95581cfad9e3f5f773bedd5b9163bc))

## [1.3.1](https://github.com/entur/helm-charts/compare/common-v1.3.0...common-v1.3.1) (2022-12-05)

### Bug Fixes

- apply seccomprofile on pods ([#43](https://github.com/entur/helm-charts/issues/43)) ([4f4989f](https://github.com/entur/helm-charts/commit/4f4989f5da955ad816b870a09e83ca25f4867fff))

## [1.3.0](https://github.com/entur/helm-charts/compare/common-v1.2.9...common-v1.3.0) (2022-12-02)

### Features

- multiple ports, multiple deployments, override pdb and hpa ([#40](https://github.com/entur/helm-charts/issues/40)) ([409ddb2](https://github.com/entur/helm-charts/commit/409ddb2ab84e9a5404e427b59d67e09af08e57b2))

## [1.2.9](https://github.com/entur/helm-charts/compare/common-v1.2.8...common-v1.2.9) (2022-11-09)

### Bug Fixes

- add terminationGracePeriodSeconds ([#37](https://github.com/entur/helm-charts/issues/37)) ([b0950a5](https://github.com/entur/helm-charts/commit/b0950a51f1ea71779dbd840d7beb75bd51f95ec9))

## [1.2.8](https://github.com/entur/helm-charts/compare/common-v1.2.7...common-v1.2.8) (2022-11-08)

### Bug Fixes

- enable grpc from common chart ([#33](https://github.com/entur/helm-charts/issues/33)) ([4645c9f](https://github.com/entur/helm-charts/commit/4645c9f3a6357df81d4083b4dc893cc1afcf65cb))

## [1.2.7](https://github.com/entur/helm-charts/compare/common-v1.2.6...common-v1.2.7) (2022-10-18)

### Bug Fixes

- add pdb for forceReplicas > 1 ([#28](https://github.com/entur/helm-charts/issues/28)) ([5871ef9](https://github.com/entur/helm-charts/commit/5871ef956407dadd15f6c22122352d7da16efd07))

## [1.2.6](https://github.com/entur/helm-charts/compare/common-v1.2.5...common-v1.2.6) (2022-10-11)

### Bug Fixes

- Allow setting cloudsqlproxy resource limits via values ([#26](https://github.com/entur/helm-charts/issues/26)) ([31cc0c8](https://github.com/entur/helm-charts/commit/31cc0c87b4b52efc317daedc8e67f8e206a85f73))

## [1.2.5](https://github.com/entur/helm-charts/compare/common-v1.2.4...common-v1.2.5) (2022-09-12)

### Bug Fixes

- replicas bug and force 1 must lead to strategy recreate ([#23](https://github.com/entur/helm-charts/issues/23)) ([32ee5bb](https://github.com/entur/helm-charts/commit/32ee5bb543eaeed7f956ba7e98e3e42b3bf71ea2))

## [1.2.4](https://github.com/entur/helm-charts/compare/common-v1.2.3...common-v1.2.4) (2022-09-05)

### Bug Fixes

- enable flag for service, default on ([#21](https://github.com/entur/helm-charts/issues/21)) ([402aa04](https://github.com/entur/helm-charts/commit/402aa04a2e53d2245f179df315794a9cfc471b29))

## [1.2.3](https://github.com/entur/helm-charts/compare/common-v1.2.2...common-v1.2.3) (2022-09-01)

### Bug Fixes

- enable custom values through common.values closes [#11](https://github.com/entur/helm-charts/issues/11) ([#18](https://github.com/entur/helm-charts/issues/18)) ([84101e9](https://github.com/entur/helm-charts/commit/84101e97280dface4129cd9139bd37f3011a5f93))

## [1.2.2](https://github.com/entur/helm-charts/compare/common-v1.2.1...common-v1.2.2) (2022-08-26)

### Bug Fixes

- mulf hack bug, closes [#7](https://github.com/entur/helm-charts/issues/7) ([#16](https://github.com/entur/helm-charts/issues/16)) ([6a339f1](https://github.com/entur/helm-charts/commit/6a339f1ece522f3cb3ad23a695ed977562587f1a))

## [1.2.1](https://github.com/entur/helm-charts/compare/common-v1.2.0...common-v1.2.1) (2022-08-26)

### Bug Fixes

- enable forceReplicas to run with 1 replica in prod ([#14](https://github.com/entur/helm-charts/issues/14)) ([7c6319e](https://github.com/entur/helm-charts/commit/7c6319e0eca134c55506d768df121268a9bd06b3))

## [1.2.0](https://github.com/entur/helm-charts/compare/common-v1.1.0...common-v1.2.0) (2022-08-23)

### Features

- add environment label ([#10](https://github.com/entur/helm-charts/issues/10)) ([c8d7a90](https://github.com/entur/helm-charts/commit/c8d7a90a5414b216314b940c2969d82842cdd18e))

## [1.1.0](https://github.com/entur/helm-charts/compare/common-1.0.2...common-v1.1.0) (2022-07-28)

### Features

- add gha workflows ([#6](https://github.com/entur/helm-charts/issues/6)) ([7ce6d29](https://github.com/entur/helm-charts/commit/7ce6d2987eb027f176a5836e81ed61e7c12066ff))
