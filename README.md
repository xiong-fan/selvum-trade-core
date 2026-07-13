# Selvum Trade Core

这是从原始 `CoinExchange_CryptoExchange_Java-master` 仓库抽取出来的最小可构建后端子集仓库，保留了 `00_framework/` 目录层级，目标是提供一个可独立构建的 Maven 子集，而不是完整业务全家桶。

目标服务共 5 个：

- `cloud`
- `wallet`
- `matching-core-service`
- `exchange-api`
- `market`

共享模块共 3 个：

- `core`
- `exchange-core`
- `exchange-core2-engine`

构建前提：

- JDK 8
- Maven 3.9.x

唯一验证命令：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
cd 00_framework
mvn -Pdev -pl core,exchange-core,exchange-core2-engine,cloud,wallet,matching-core-service,exchange-api,market -am -DskipTests package
```

说明：

- 本仓库中的配置文件按当前 checkout 原样保留，未做脱敏或键名调整。
