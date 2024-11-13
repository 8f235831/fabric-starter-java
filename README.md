# Fabric-Starter-Java
![GitHub License](https://img.shields.io/github/license/8f235831/fabric-starter-java)

一个基于 [Fabric Sample](https://github.com/hyperledger/fabric-samples) 中`test-network`网络及相关工具搭建的 Fabric Chaincode / Application 开发测试用项目框架，项目语言采用 Java 11。此项目不适用于生产目的。

项目采用 Gradle 多模块框架，集成了 Chaincode 的远程部署，并支持在本地运行或测试 Application。 

项目基于 Gradle Plugin + JavaPoet 实现了合约 API 定义的自动生成，通过同一份配置为 Chaincode 和 Application 分别生成各自适用的 Java 代码文件。

### Fabric 服务器配置
运行项目前，需要在 Linux 服务器上部署Fabric环境。

#### 虚拟机配置
如不使用虚拟机，跳过这一部分。

* 安装 Oracle VM VirtualBox ；
* 安装系统镜像 Ubuntu-24.10-live-server-amd64；
* 配置NAT与Host-Only双网卡实现宿主机与虚拟机间的双向访问；
* （根据所处网络环境）配置虚拟机允许其使用宿主机代理访问互联网，勾选“自动检测主机代理设置即可”；
  > 用于访问Apt源、Docker镜像和Github。

#### Sample安装
这一部分中，安装Fabric Sample的所需环境，并下载安装Sample。

这一部分中应当始终启用代理网络。

下面的代码将把 Fabric 的相关程序安装在 `~/fabric` 目录下。

```shell
# this script is used to configure fabric sample.
# configure proxy before use this script and use `sudo` to grant root access.
# execute script at a directory where to install fabric sample

cd ~
mkdir fabric
cd ./fabric

# configure fabric prerequisites: git, curl， docker and openjdk-11
sudo apt-get install git curl docker-compose openjdk-11-jdk -y

# Make sure the Docker daemon is running.
sudo systemctl start docker

# Add user to the Docker group.
sudo usermod -a -G docker "$USER"

# Check version numbers
# docker --version
# docker-compose --version

# enable auto start docker service when rebooting machine
sudo systemctl enable docker

# configure docker mirror sites
# sudo echo -e "{\n\t\"registry-mirrors\": [\n\t\t\"https://dockerpull.com\",\n\t\t\"https://docker.1panel.dev\",\n\t\t\"https://docker.fxxk.dedyn.io\",\n\t\t\"https://docker.zhai.cm\",\n\t\t\"https://hub.geekery.cn\",\n\t\t\"https://a.ussh.net\",\n\t\t\"https://atomhub.openatom.cn\",\n\t\t\"https://docker.m.daocloud.io\",\n\t\t\"https://docker.xn--6oq72ry9d5zx.cn\"\n\t]\n}" > /etc/docker/daemon.json

######################
# download installer shell script file
curl -sSLO https://raw.githubusercontent.com/hyperledger/fabric/main/scripts/install-fabric.sh && chmod +x install-fabric.sh

# download docker mirror, sample and cli binary
./install-fabric.sh docker samples binary

```
#### 运行Sample网络以测试环境
略，详见[Fabric官方文档](https://hyperledger-fabric.readthedocs.io/zh-cn/latest/test_network.html)。

#### 手动启动测试合约
在虚拟机上执行下面的命令。

```shell
cd ~/fabric/fabric-samples/test-network

# clear and run test network.
./network.sh down
./network.sh up createChannel -c mychannel -ca

# compile and deploy chiancode.
./network.sh deployCC -ccn basic -ccp ../asset-transfer-basic/chaincode-java/ -ccl java

# compile and run application.
cd ~/fabric/fabric-samples/asset-transfer-basic/application-gateway-java/
./gradlew build run
```

### 项目运行说明
运行前需要修改`./config.properties`文件，配置 Fabric 服务器相关项。

在本地项目目录下执行下面的指令，重置 Fabric 网络，部署 Chaincode，并运行 Application：

```shell
./gradlew chaincode:deployChaincode application:clean application:run
```

#### 重置 Fabric 网络
```shell
./gradlew chaincode:restartNetwork
```

#### 部署 Chaincode
> 依赖任务 `chaincode:restartNetwork`。
```shell
./gradlew chaincode:deployChaincode
```

#### 运行 Application

```shell
./gradlew application:clean application:run
```

### 如何基于此项目实现自己的智能合约
> 需要提前搭建好 Fabric 服务器相关环境，详细步骤参见上文。

1. 修改`./chaincodeDefinition.gradle`文件中的合约接口定义；
2. 执行`./gradlew chaincode:build application:build`，生成合约 API 定义代码；
3. 修改`./chaincode`下的源码文件，继承生成的API接口并按照Fabric Chaincode规范添加必要的注解和接口，实现具体的方法以完成合约程序；
4. 部署 Chaincode；
5. 修改`./application`下的源码文件，根据生成的API构造对象并通过方法访问Fabric合约，以此实现具体的应用程序；
6. 运行 Application。
