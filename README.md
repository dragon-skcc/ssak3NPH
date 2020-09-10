# Ssak3 - 청소대행 서비스

# 목차

  - [서비스 시나리오](#서비스-시나리오)
  - [분석/설계](#분석설계)
  - [구현](#구현)
    - [DDD 의 적용](#DDD-의-적용)
    - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
    - [폴리글랏 프로그래밍](#폴리글랏-프로그래밍)
    - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)
    - [비동기식 호출 과 Eventual Consistency](#비동기식-호출과-Eventual-Consistency)
  - [운영](#운영)
    - [CI/CD 설정](#CI/CD-설정)
    - [동기식 호출 / 서킷 브레이킹 / 장애격리](#동기식-호출-/-서킷-브레이킹-/-장애격리)
    - [오토스케일 아웃](#오토스케일-아웃)
    - [무정지 재배포](#무정지-재배포)
    - [ConfigMap 사용](#ConfigMap-사용)


# 서비스 시나리오
  
## 기능적 요구사항
1. 고객이 청소를 요청하면 결제가 완료된다(Sync, 결제서비스)
2. 청소업체가 청소를 완료한다
3. 청소가 완료되면, 고객에게 완료되었다고 전달한다 (Async, 알림서비스)
4. 결제가 완료되면, 결제 & 예약 내용을 청소업체에게 전달한다 (Async, 알림서비스)
5. 고객은 본인의 예약 내용 및 상태를 조회한다
6. 고객은 본인의 예약을 취소할 수 있다
7. 예약이 취소되면, 결제를 취소한다. (Async, 결제서비스)
8. 결제가 취소되면, 결제 취소 내용을 청소업체에게 전달한다 (Async, 알림서비스)
9. 청소부를 입력하면 청소부에게 등록되었다고 전달한다 (Async, 알림서비스)
10 청소부가 카카오 알림을 요청하면 카카오 알림이 발송된다(Sync, 알림서비스)

## 비기능적 요구사항
### 1. 트랜잭션
- 결제가 되지 않은 예약건은 아예 거래가 성립되지 않아야 한다 → Sync 호출 
### 2. 장애격리
- 통지(알림) 기능이 수행되지 않더라도 예약은 365일 24시간 받을 수 있어야 한다 - Async (event-driven), Eventual Consistency
- 결제시스템이 과중되면 사용자를 잠시동안 받지 않고 결제를 잠시후에 하도록 유도한다 → Circuit breaker, fallback
### 3. 성능
- 고객과 청소업체가 자주 예약관리에서 확인할 수 있는 상태를 마이페이지(프론트엔드)에서 확인할 수 있어야 한다 → CQRS
- 상태가 바뀔때마다 카톡 등으로 알림을 줄 수 있어야 한다 → Event driven

# 분석/설계

청소예약 및 취소 시 Saga패턴(예약 Req/Resp, 취소 Pub/Sub)을 적용하여 구현되도록 설계함

## Event Storming 결과
* MSAEz 로 모델링한 이벤트스토밍 결과 : 팀과제 http://www.msaez.io/#/storming/k1eXHY4YSrSFKU3UpQTDRHUvSS23/every/f5d0809e09167fd49a1a95acfc9dd0d2/-MGcF3GTrAc5KsEkYr8b
* 개인 추가 내역
![image](https://user-images.githubusercontent.com/69634194/92623947-e9b9b480-f301-11ea-95d3-3a35c4689934.png)



# 구현/배포(deploy)
분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현하였다. 배포는 아래와 같이 수행한다.

## Azure Configure
```console
- Azure (http://portal.azure.com) : TeamA@gkn2019hotmail.onmicrosoft.com
- AZure 포탈에서 리소스 그룹 > 쿠버네티스 서비스 생성 > 컨테이너 레지스트리 생성
- 리소스 그룹 생성 : ssak3-rg
- 컨테이너 생성( Kubernetes ) : ssak3-aks
- 레지스트리 생성 : ssak3acr, ssak3acr2.azurecr.io
- azure container repository 이름 : cleaning
- container registry image : ssak3acr.azurecr.io/reservation, payment....
```

## 접속환경
- Azure 포탈에서 가상머신 신규 생성 - ubuntu 18.04

## Kubectl install
```
sudo apt-get update && sudo apt-get install -y apt-transport-https
curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key add -
echo "deb https://apt.kubernetes.io/ kubernetes-xenial main" | sudo tee -a /etc/apt/sources.list.d/kubernetes.list
sudo apt-get update
sudo apt-get install -y kubectl
```

## Azure-Cli  install
```console
# curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash
# az login -u  -p
```


## Azure 인증
```console
# az login
# az aks get-credentials --resource-group ssak3-rg --name ssak3-aks
# az acr login --name ssak3acr2 --expose-token

```

## Azure AKS와 ACR 연결
```console
az aks update -n ssak3-aks -g ssak3-rg --attach-acr ssak3acr
```

## kubectl로 확인
```console
kubectl config current-context
kubectl get all
```

## jdk설치
```console
sudo apt-get update
sudo apt install default-jdk
[bash에 환경변수 추가]
1. cd ~
2. nano .bashrc 
3. 편집기 맨 아래로 이동
4. (JAVA_HOME 설정 및 실행 Path 추가)
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH=$PATH:$JAVA_HOME/bin:.

ctrl + x, y 입력, 종료
source ~/.bashrc
5. 설치확인
echo $JAVA_HOME
java -version

```

## 리눅스에 Docker client 설치
```console
sudo apt-get update
sudo apt install apt-transport-https ca-certificates curl software-properties-common
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add 
sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu bionic stable"
sudo apt update
sudo apt install docker-ce
# 리눅스 설치시 생성한 사용자 명 입력
sudo usermod -aG docker skccadmin
```

## 리눅스에 docker demon install
```console
sudo apt-get update
sudo apt-get install \
     apt-transport-https \
     ca-certificates \
     curl \
     gnupg-agent \
     software-properties-common

curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
sudo apt-key fingerprint 0EBFCD88

sudo add-apt-repository \
     "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
     $(lsb_release -cs) \
     stable"

sudo apt-get update
sudo apt-get install docker-ce docker-ce-cli containerd.io


(demon server 시작)
sudo service docker start
(확인)
docker version
sudo docker run hello-world

```

## Docker demon과 Docker client 연결
```console
cd
nano .bashrc
맨아래 줄에 아래 환경변수 추가
방향키로 맨 아래까지 내린 다음, 새로운 행에 아래 내용 입력
export DOCKER_HOST=tcp://0.0.0.0:2375 
저장 & 종료 : Ctrl + x, 입력 후, y 입력  후 엔터
source ~/.bashrc
```

## Kafka install (kubernetes/helm)
참고 - (https://workflowy.com/s/msa/27a0ioMCzlpV04Ib#/a7018fb8c62)
```console

curl https://raw.githubusercontent.com/kubernetes/helm/master/scripts/get | bash
kubectl --namespace kube-system create sa tiller      
kubectl create clusterrolebinding tiller --clusterrole cluster-admin --serviceaccount=kube-system:tiller
helm init --service-account tiller
kubectl patch deploy --namespace kube-system tiller-deploy -p '{"spec":{"template":{"spec":{"serviceAccount":"tiller"}}}}'

helm repo add incubator http://storage.googleapis.com/kubernetes-charts-incubator
helm repo update

helm install --name my-kafka --namespace kafka incubator/kafka
```

## Kafka delete
```console
helm del my-kafka  --purge
```


## Istio 설치
```console
kubectl create namespace istio-system

curl -L https://git.io/getLatestIstio | ISTIO_VERSION=1.4.5 sh -
cd istio-1.4.5
export PATH=$PWD/bin:$PATH
for i in install/kubernetes/helm/istio-init/files/crd*yaml; do kubectl apply -f $i; done
kubectl apply -f install/kubernetes/istio-demo.yaml
kubectl get pod -n istio-system
```

## kiali 설치
```console

vi kiali.yaml    

apiVersion: v1
kind: Secret
metadata:
  name: kiali
  namespace: istio-system
  labels:
    app: kiali
type: Opaque
data:
  username: YWRtaW4=
  passphrase: YWRtaW4=

----- save (:wq)

kubectl apply -f kiali.yaml
helm template --set kiali.enabled=true install/kubernetes/helm/istio --name istio --namespace istio-system > kiali_istio.yaml    
kubectl apply -f kiali_istio.yaml
```
- load balancer로 변경
```console
kubectl edit service/kiali -n istio-system
(ClusterIP -> LoadBalancer)
```

## namespace create
```console
kubectl create namespace ssak3
```
## namespace 선택 설정 (-n ssak3 옵션을 주지 않도록 default 작업 ns 설정 방법)
```console
kubectl config set-context --current --namespace=ssak3
```

## istio enabled
```console
kubectl label namespace ssak3 istio-injection=enabled
```

## siege deploy
```console
cd ssak3/yaml
kubectl apply -f siege.yaml 
kubectl exec -it siege -n ssak3 -- /bin/bash
apt-get update
apt-get install httpie
```

## image build & push
- compile
```console
cd ssak3/gateway
mvn package
```

- for azure cli
```console
docker build -t ssak3acr.azurecr.io/gateway .
docker images
docker push ssak3acr.azurecr.io/gateway
```

![image](https://user-images.githubusercontent.com/69634194/92604257-48276880-f2eb-11ea-9cde-c596732d3b37.png)

## application deploy
```console
kubectl create deploy gateway --image=ssak3acr2.azurecr.io/gateway -n ssak3
kubectl create deploy reservation --image=ssak3acr2.azurecr.io/reservation -n ssak3
kubectl create deploy cleaning --image=ssak3acr2.azurecr.io/cleaning -n ssak3
kubectl create deploy dashboard --image=ssak3acr2.azurecr.io/dashboard -n ssak3
kubectl create deploy message --image=ssak3acr2.azurecr.io/message -n ssak3
kubectl create deploy payment --image=ssak3acr2.azurecr.io/payment -n ssak3
kubectl create deploy cleanerregistration --image=ssak3acr2.azurecr.io/cleanerregistration -n ssak3
kubectl create deploy kakaoapi --image=ssak3acr2.azurecr.io/kakaoapi -n ssak3

kubectl expose deploy gateway --port=8080 -n ssak3
kubectl expose deploy reservation --port=8080 -n ssak3
kubectl expose deploy cleaning --port=8080 -n ssak3
kubectl expose deploy dashboard --port=8080 -n ssak3
kubectl expose deploy message --port=8080 -n ssak3
kubectl expose deploy payment --port=8080 -n ssak3
kubectl expose deploy cleanerregistration --port=8080 -n ssak3
kubectl expose deploy kakaoapi --port=8080 -n ssak3

cd ssak3/yaml

kubectl apply -f configmap.yaml
kubectl apply -f gateway.yaml
kubectl apply -f cleaning.yaml
kubectl apply -f reservation.yaml
kubectl apply -f payment.yaml
kubectl apply -f dashboard.yaml
kubectl apply -f message.yaml
```

## DDD 의 적용
* 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다: (예시는 청소부  마이크로서비스).
  - 가능한 현업에서 사용하는 언어 (유비쿼터스 랭귀지)를 그대로 사용할 수 있지만, 일부 구현에 있어서 영문이 아닌 경우는 실행이 불가능한 경우가 있다 Maven pom.xml, Kafka의 topic id, FeignClient 의 서비스 id 등은 한글로 식별자를 사용하는 경우 오류가 발생하는 것을 확인하였다)
  - 최종적으로는 모두 영문을 사용하였으며, 이는 잠재적인 오류 발생 가능성을 차단하고 향후 확장되는 다양한 서비스들 간에 영향도를 최소화하기 위함이다.
```java
package CleaningServiceYD;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Cleaner_table")
public class Cleaner {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long cleanerId;
    private Integer cleanerName;
    private String cleanerPNumber;
    private String status;

    @PostPersist
    public void onPostPersist(){

    	System.out.println("##### Payment onPostPersist : " + getStatus());

    	 if("CleanerRegistered".equals(getStatus())) {

      	  CleanerRegistered cleanerRegistered = new CleanerRegistered();
          BeanUtils.copyProperties(this, cleanerRegistered);
		
	  cleanerRegistered.setCleanerId(getCleanerId());
	  cleanerRegistered.setCleanerName(getCleanerName());
	  cleanerRegistered.setCleanerPNumber(getPNumber());
          cleanerRegistered.setStatus("CleanerRegisteredCompleted");		
          cleanerRegistered.publishAfterCommit();
		
	 else if("KakaoRegisterCompleted".equals(getStatus())){
	    KakaoRegistered kakaoRegistered = new KakaoRegistered();		
            BeanUtils.copyProperties(this, kakaoRegistered);
		
	    cleanerRegistered.setCleanerId(getCleanerId());
	    cleanerRegistered.setCleanerName(getCleanerName());
	    cleanerRegistered.setCleanerPNumber(getPNumber());
            cleanerRegistered.setStatus("CleanerRegisteredCompleted");
             kakaoRegistered.publishAfterCommit();
	}

    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getCleanerId() {
        return cleanerId;
    }

    public void setCleanerId(Long cleanerId) {
        this.cleanerId = cleanerId;
    }
    public String getCleanerName() {
        return cleanerName;
    }
    .....
   
}

```
- Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 다양한 데이터소스 유형 (RDB or NoSQL) 에 대한 별도의 처리가 없도록 데이터 접근 어댑터를 자동 생성하기 위하여 Spring Data REST 의 RestRepository 를 적용하였다
```java
package CleaningServiceYD;

import org.springframework.data.repository.PagingAndSortingRepository;

public interface PaymentRepository extends PagingAndSortingRepository<Payment, Long>{


}
```

- API Gateway 적용
```console
# gateway service type 변경(External IP 나오게함)
$ kubectl edit service/gateway -n ssak3
(ClusterIP -> LoadBalancer)

root@ssak3-vm:/home/skccadmin/ssak3NPH/yaml# kubectl get service -n ssak3
NAME                  TYPE           CLUSTER-IP     EXTERNAL-IP    PORT(S)          AGE
cleanerregistration   ClusterIP      10.0.126.238   <none>         8080/TCP         102m
cleaning              ClusterIP      10.0.244.177   <none>         8080/TCP         105m
dashboard             ClusterIP      10.0.89.35     <none>         8080/TCP         104m
gateway               LoadBalancer   10.0.14.255    20.41.120.55   8080:30364/TCP   56m
kakaoapi              ClusterIP      10.0.167.56    <none>         8080/TCP         102m
message               ClusterIP      10.0.9.94      <none>         8080/TCP         104m
payment               ClusterIP      10.0.129.175   <none>         8080/TCP         104m
reservation           ClusterIP      10.0.23.51     <none>         8080/TCP         105m```


- API Gateway 적용 확인
```console
//청소부 등록
http POST http://20.41.120.55:8080/cleaningRegistration cleanerID=1 cleanerName=NPH cleanerPNumber=01012341234 
```


root@ssak3-vm:/home/skccadmin/ssak3NPH/yaml# http POST http://20.41.120.55:8080/cleaningRegistration cleanerID=1 cleanerName=NPH cleanerPNumber=01012341234
HTTP/1.1 201 Created
content-type: application/json;charset=UTF-8
date: Wed, 09 Sep 2020 15:54:35 GMT
location: http://cleaning:8080/cleans/1
server: envoy
transfer-encoding: chunked
x-envoy-upstream-service-time: 676

{
    "_links": {
        "clean": {
            "href": "http://cleaner:8080/cleaner/1"
        },
        "self": {
            "href": "http://cleaner:8080/cleaner/1"
        }
    },
    "cleanerID": "1",
    "cleanerName": NPH,
    "cleanPNumber": "01012341234"
}


- siege 접속
```console
kubectl exec -it siege -n cleaning -- /bin/bash
```

- (siege 에서) 적용 후 REST API 테스트 
```
# 청소 서비스 예약요청 처리
http POST http://reservation:8080/cleaningReservations requestDate=20200907 place=seoul status=ReservationApply price=2000 customerName=yeon

# 예약 상태 확인
http http://reservation:8080/reservations/1

# 예약취소 
http DELETE http://reservation:8080/cleaningReservations/1

# 청소 결과 등록
http POST http://cleaning:8080/cleanerRegistration status=CleaningStarted requestId=1 cleanDate=20200909
```


## 동기식 호출 과 Fallback 처리
분석단계에서의 조건 중 하나로 등록->카카오 연동 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

- 결제 서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 
```java
@FeignClient(name="Payment", url="${api.url.payment}")
public interface PaymentService {

    @RequestMapping(method= RequestMethod.POST, path="/cleaner")
    public void payRequest(@RequestBody Cleaner cleaner);

}
```
- 카카오 알림 받은 직후(@PostPersist) 결제가 완료되도록 처리
```java
@Entity
@Table(name="Cleaner_table")
public class CleanerReservation {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long CleanerID;
    private String CleanerName;
    private Long CleanerPNumber;

    @PostPersist
    public void onPostPersist(){

    	System.out.println("##### Payment onPostPersist : " + getStatus());

    	 if("CleanerRegistered".equals(getStatus())) {

      	  CleanerRegistered cleanerRegistered = new CleanerRegistered();
          BeanUtils.copyProperties(this, cleanerRegistered);
		
	  cleanerRegistered.setCleanerId(getCleanerId());
	  cleanerRegistered.setCleanerName(getCleanerName());
	  cleanerRegistered.setCleanerPNumber(getPNumber());
          cleanerRegistered.setStatus("CleanerRegisteredCompleted");		
          cleanerRegistered.publishAfterCommit();
		
	 else if("KakaoRegisterCompleted".equals(getStatus())){
	    KakaoRegistered kakaoRegistered = new KakaoRegistered();		
            BeanUtils.copyProperties(this, kakaoRegistered);
		
	    cleanerRegistered.setCleanerId(getCleanerId());
	    cleanerRegistered.setCleanerName(getCleanerName());
	    cleanerRegistered.setCleanerPNumber(getPNumber());
            cleanerRegistered.setStatus("CleanerRegisteredCompleted");
             kakaoRegistered.publishAfterCommit();
	}
}
```

- 호출 시간에 따른 타임 커플링이 발생하며, 결제 시스템이 장애가 나면 주문도 못받는다는 것을 확인
```
# 결제 서비스를 잠시 내려놓음
$ kubectl delete -f cleaner.yaml

# 예약처리 (siege 에서)
http POST http://reservation:8080/cleanerReservations cleanerID=1 cleanerName=NPH cleanerPNumber=01012341234

# 예약처리 시 에러 내용
HTTP/1.1 500 Internal Server Error
content-type: application/json;charset=UTF-8
date: Tue, 08 Sep 2020 15:51:34 GMT
server: envoy
transfer-encoding: chunked
x-envoy-upstream-service-time: 87

{
    "error": "Internal Server Error",
    "message": "Could not commit JPA transaction; nested exception is javax.persistence.RollbackException: Error while committing the transaction",
    "path": "/cleaningReservations",
    "status": 500,
    "timestamp": "2020-09-09T15:51:34.959+0000"
}

# 결제서비스 재기동
$ kubectl apply -f cleaner.yaml

NAME                           READY   STATUS    RESTARTS   AGE
cleaning-bf474f568-vxl8r       2/2     Running   0          147m
dashboard-7f7768bb5-7l8wr      2/2     Running   0          145m
gateway-6dfcbbc84f-rwnsh       2/2     Running   0          47m
message-69597f6864-mhwx7       2/2     Running   0          147m
payment-7749f7dc7c-kfjxb       2/2     Running   0          88s
reservation-775fc6574d-kddgd   2/2     Running   0          153m
siege                          2/2     Running   0          3h48m


# 예약처리 (siege 에서)
http POST http://cleaner:8080/cleanerReservations cleanerID=3 cleanerName=NPH cleanerPNumber=01012341234

# 처리결과
HTTP/1.1 201 Created
content-type: application/json;charset=UTF-8
date: Tue, 08 Sep 2020 15:58:28 GMT
location: http://cleaner:8080/cleanerReservations/5
server: envoy
transfer-encoding: chunked
x-envoy-upstream-service-time: 113

{
    "_links": {
        "cleanerReservation": {
            "href": "http://cleaner:8080/cleanerReservations/5"
        },
        "self": {
            "href": "http://cleaner:8080/cleanerReservations/5"
        }
    },
    "cleanerID": "3",
    "cleanerName": "NPH",
    "cleanerPNumber": 01012341234"
}
```
- 과도한 요청시에 서비스 장애가 도미노 처럼 벌어질 수 있다 (서킷브레이커, 폴백 처리는 운영단계에서 설명)

## 비동기식 호출과 Eventual Consistency
- 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트
결제가 이루어진 후에 알림 처리는 동기식이 아니라 비 동기식으로 처리하여 알림 시스템의 처리를 위하여 예약이 블로킹 되지 않아도록 처리한다.
 
- 이를 위하여 예약관리, 결제관리, 청소부 등록 카카오 알림에 기록을 남긴 후에 곧바로 완료되었다는 도메인 이벤트를 카프카로 송출한다(Publish)
```java
@Entity
@Table(name="Cleaner_table")
public class Cleaner {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
     private Long CleanerID;
    private String CleanerName;
    private Long CleanerPNumber;

    @PostPersist
    public void onPostPersist(){

    	System.out.println("##### Payment onPostPersist : " + getStatus());

    	 if("CleanerRegistered".equals(getStatus())) {

      	  CleanerRegistered cleanerRegistered = new CleanerRegistered();
          BeanUtils.copyProperties(this, cleanerRegistered);
		
	  cleanerRegistered.setCleanerId(getCleanerId());
	  cleanerRegistered.setCleanerName(getCleanerName());
	  cleanerRegistered.setCleanerPNumber(getPNumber());
          cleanerRegistered.setStatus("CleanerRegisteredCompleted");		
          cleanerRegistered.publishAfterCommit();
		
	 else if("KakaoRegisterCompleted".equals(getStatus())){
	    KakaoRegistered kakaoRegistered = new KakaoRegistered();		
            BeanUtils.copyProperties(this, kakaoRegistered);
		
	    cleanerRegistered.setCleanerId(getCleanerId());
	    cleanerRegistered.setCleanerName(getCleanerName());
	    cleanerRegistered.setCleanerPNumber(getPNumber());
            cleanerRegistered.setStatus("CleanerRegisteredCompleted");
             kakaoRegistered.publishAfterCommit();
	}

    }
    ...
}
```
- 알림 서비스에서는 결제승인, 청소완료, 결제취소, 청소부 등록 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다
```java
@Service
public class PolicyHandler{

	@Autowired
    private MessageRepository messageRepository;

      @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCleanerRegistered_MessageAlert(@Payload CleanerRegistered CleanerRegistered){

        if(CleanerRegistered.isMe()){
            Message message = new Message();

            message.setCleanerID(CleanerRegistered.getCleanerID());
            message.setCleanerName(CleanerRegistered.getCleanerName());
            message.setCleanerPNumber(CleanerRegistered.getCleanerPNumber());

            messageRepository.save(message);

            System.out.println("##### listener MessageAlert : " + CleanerRegistered.toJson());
        }
    }
```

* 알림 시스템은 예약/결제와 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 알림 시스템이 유지보수로 인해 잠시 내려간 상태라도 예약을 받는데 문제가 없다

```
# 알림 서비스를 잠시 내려놓음
kubectl delete -f message.yaml

# 예약처리 (siege 에서)
http POST http://reservation:8080/cleaningReservations requestDate=20200907 place=seoul status=ReservationApply price=250000 customerName=chae #Success
http POST http://reservation:8080/cleaningReservations requestDate=20200909 place=pangyo status=ReservationApply price=300000 customerName=noh #Success

# 알림이력 확인 (siege 에서)
http http://message:8080/messages # 알림이력조회 불가

http: error: ConnectionError: HTTPConnectionPool(host='message', port=8080): Max retries exceeded with url: /messages (Caused by NewConnectionError('<urllib3.connection.HTTPConnection object at 0x7fae6595deb8>: Failed to establish a new connection: [Errno -2] Name or service not known')) while doing GET request to URL: http://message:8080/messages

# 알림 서비스 기동
kubectl apply -f message.yaml

# 알림이력 확인 (siege 에서)
http http://message:8080/messages # 알림이력조회

HTTP/1.1 200 OK
content-type: application/hal+json;charset=UTF-8
date: Tue, 08 Sep 2020 16:01:45 GMT
server: envoy
transfer-encoding: chunked
x-envoy-upstream-service-time: 439

{
    "_embedded": {
        "messages": [
            {
                "_links": {
                    "message": {
                        "href": "http://message:8080/messages/1"
                    },
                    "self": {
                        "href": "http://message:8080/messages/1"
                    }
                },
                "requestId": 6,
                "status": "PaymentCompleted"
            },
            {
                "_links": {
                    "message": {
                        "href": "http://message:8080/messages/2"
                    },
                    "self": {
                        "href": "http://message:8080/messages/2"
                    }
                },
                "requestId": 7,
                "status": "PaymentCompleted"
            }
        ]
    },
    "_links": {
        "profile": {
            "href": "http://message:8080/profile/messages"
        },
        "self": {
            "href": "http://message:8080/messages{?page,size,sort}",
            "templated": true
        }
    },
    "page": {
        "number": 0,
        "size": 20,
        "totalElements": 2,
        "totalPages": 1
    }
}
```

# 운영

## CI/CD 설정
  * 각 구현체들은 github의 각각의 source repository 에 구성
  * 별도 VM에 Docker 설치하여 Docker build 및 Docker Push를 Azure로 함
  * Image repository는 Azure 사용

## 동기식 호출 / 서킷 브레이킹 / 장애격리

### 서킷 브레이킹 프레임워크의 선택: istio-injection + DestinationRule

* istio-injection 적용
```
kubectl label namespace ssak3 istio-injection=enabled

# error: 'istio-injection' already has a value (enabled), and --overwrite is false
```
* 예약, 결제 서비스,  모두 아무런 변경 없음

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인:
- 동시사용자 100명
- 60초 동안 실시
```console
siege -v -c100 -t60S -r10 --content-type "application/json" 'http://cleaner:8080/cleanerReservations POST {"cleanerID": "5","cleanerName": NPH,"cleanPNumber": "01012341234"}'

HTTP/1.1 201     1.20 secs:     341 bytes ==> POST http://cleaner:8080/cleanerReservations
HTTP/1.1 201     1.12 secs:     341 bytes ==> POST http://cleaner:8080/cleanerReservations
HTTP/1.1 201     0.14 secs:     341 bytes ==> POST http://cleaner:8080/cleanerReservations
HTTP/1.1 201     1.11 secs:     341 bytes ==> POST http://cleaner:8080/cleanerReservations
HTTP/1.1 201     1.21 secs:     341 bytes ==> POST http://cleaner:8080/cleanerReservations
HTTP/1.1 201     1.20 secs:     341 bytes ==> POST http://cleaner:8080/cleanerReservations
HTTP/1.1 201     1.20 secs:     341 bytes ==> POST http://cleaner:8080/cleanerReservations

Lifting the server siege...
Transactions:                   4719 hits
Availability:                 100.00 %
Elapsed time:                  59.14 secs
Data transferred:               1.53 MB
Response time:                  1.23 secs
Transaction rate:              79.79 trans/sec
Throughput:                     0.03 MB/sec
Concurrency:                   97.95
Successful transactions:        4719
Failed transactions:               0
Longest transaction:            7.29
Shortest transaction:           0.05
```
* 서킷 브레이킹을 위한 DestinationRule 적용
```
cd ssak3/yaml
kubectl apply -f cleaner_dr.yaml

# destinationrule.networking.istio.io/dr-cleaner created

HTTP/1.1 500     0.68 secs:     262 bytes ==> POST http://cleaner:8080/cleanerReservations
HTTP/1.1 500     0.70 secs:     262 bytes ==> POST http://cleaner:8080/cleanerReservations
HTTP/1.1 500     0.71 secs:     262 bytes ==> POST http://cleaner:8080/cleanerReservations
HTTP/1.1 500     0.72 secs:     262 bytes ==> POST http://cleaner:8080/cleanerReservations
HTTP/1.1 500     0.92 secs:     262 bytes ==> POST http://cleaner:8080/cleanerReservations

siege aborted due to excessive socket failure; you
can change the failure threshold in $HOME/.siegerc

Transactions:                     20 hits
Availability:                   1.75 %
Elapsed time:                   9.92 secs
Data transferred:               0.29 MB
Response time:                 48.04 secs
Transaction rate:               2.02 trans/sec
Throughput:                     0.03 MB/sec
Concurrency:                   96.85
Successful transactions:          20
Failed transactions:            1123
Longest transaction:            2.53
Shortest transaction:           0.04
```


## 오토스케일 아웃
앞서 CB 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 함
* (istio injection 적용한 경우) istio injection 적용 해제
```
kubectl label namespace ssak3 istio-injection=disabled --overwrite

# namespace/ssak3 labeled

kubectl apply -f reservation.yaml
kubectl apply -f cleaner.yaml
```
- 결제서비스 배포시 resource 설정 적용되어 있음
```
    spec:
      containers:
          ...
          resources:
            limits:
              cpu: 500m
            requests:
              cpu: 200m
```

- 결제서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 15프로를 넘어서면 replica 를 3개까지 늘려준다
```console
kubectl autoscale deploy cleanerregistration -n ssak3 --min=1 --max=3 --cpu-percent=15

# horizontalpodautoscaler.autoscaling/payment autoscaled

NAME                                                      REFERENCE                        TARGETS         MINPODS   MAXPODS   REPLICAS   AGE
horizontalpodautoscaler.autoscaling/cleanerregistration   Deployment/cleanerregistration   3%/15%   1         3         0          4s


```

- CB 에서 했던 방식대로 워크로드를 3분 동안 걸어준다.
```console
siege -v -c100 -t180S -r10 --content-type "application/json" 'http://cleaner:8080/cleanerReservations POST {"cleanerID": "3","cleanerName": NPH,"cleanerPNumber": "01012341234"}'
```

- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다
```console
kubectl get deploy cleanerregistration -n ssak3 -w 

NAME                  READY   UP-TO-DATE   AVAILABLE   AGE
cleanerregistration   1/1     1            1           13h

# siege 부하 적용 후
root@ssak3-vm:/# kubectl get deploy cleanerregistration -n ssak3 -w
NAME                  READY   UP-TO-DATE   AVAILABLE   AGE
cleanerregistration   1/1     1            1           43m
cleanerregistration   1/3     1            1           44m
cleanerregistration   1/3     1            1           44m
cleanerregistration   1/3     3            1           44m
cleanerregistration   2/3     3            2           46m
cleanerregistration   3/3     3            3           46m
```
- siege 의 로그를 보아도 전체적인 성공률이 높아진 것을 확인 할 수 있다.
```console
Lifting the server siege...
Transactions:                  19309 hits
Availability:                 100.00 %
Elapsed time:                 179.75 secs
Data transferred:               6.31 MB
Response time:                  0.92 secs
Transaction rate:             107.42 trans/sec
Throughput:                     0.04 MB/sec
Concurrency:                   99.29
Successful transactions:       19309
Failed transactions:               0
Longest transaction:            7.33
Shortest transaction:           0.01
```

## 무정지 재배포 (readiness)
- 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함 (위의 시나리오에서 제거되었음)
```console
kubectl delete horizontalpodautoscaler.autoscaling/cleanerregistration -n ssak3
```
- yaml 설정 참고
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cleaner
  namespace: ssak3
  labels:
    app: cleaner
spec:
  replicas: 1
  selector:
    matchLabels:
      app: cleaner
  template:
    metadata:
      labels:
        app: cleaner
    spec:
      containers:
        - name: cleaner
          image: ssak3acr.azurecr.io/cleanerregistration:1.0
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
          env:
            - name: api.url.payment
              valueFrom:
                configMapKeyRef:
                  name: ssak3-config
                  key: api.url.payment
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
          livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5

---

```

- siege 로 배포작업 직전에 워크로드를 모니터링 함.
```console
siege -v -c1 -t120S -r10 --content-type "application/json" 'http://cleaner:8080/cleanerReservations POST {"cleanerID": "5","cleanerName": NPH,"cleanerPNumber": "01012341234"}'
```

- 새버전으로의 배포 시작
```
# 컨테이너 이미지 Update (readness, liveness 미설정 상태)
kubectl apply -f cleaner_na.yaml
```

- siege 의 화면으로 넘어가서 Availability 가 100% 미만으로 떨어졌는지 확인
```console
Lifting the server siege...
Transactions:                  22984 hits
Availability:                  98.68 %
Elapsed time:                 299.64 secs
Data transferred:               7.52 MB
Response time:                  0.01 secs
Transaction rate:              76.71 trans/sec
Throughput:                     0.03 MB/sec
Concurrency:                    0.97
Successful transactions:       22984
Failed transactions:             308
Longest transaction:            0.97
Shortest transaction:           0.00

```

- 배포기간중 Availability 가 평소 100%에서 98% 대로 떨어지는 것을 확인. 
- 원인은 쿠버네티스가 성급하게 새로 올려진 서비스를 READY 상태로 인식하여 서비스 유입을 진행한 것이기 때문. 이를 막기위해 Readiness Probe 를 설정함:
```console
# deployment.yaml 의 readiness probe 의 설정:
kubectl apply -f cleaner.yaml

NAME                               READY   STATUS    RESTARTS   AGE
pod/cleaning-bf474f568-vxl8r       2/2     Running   0          4h3m
pod/dashboard-7f7768bb5-7l8wr      2/2     Running   0          4h1m
pod/gateway-6dfcbbc84f-rwnsh       2/2     Running   0          143m
pod/message-69597f6864-fjs69       2/2     Running   0          92m
pod/payment-7749f7dc7c-kfjxb       2/2     Running   0          97m
pod/cleaner-775fc6574d-nfnxx       1/1     Running   0          3m54s
pod/siege                          2/2     Running   0          5h24m

```
- 동일한 시나리오로 재배포 한 후 Availability 확인
```console
Lifting the server siege...
Transactions:                   6663 hits
Availability:                 100.00 %
Elapsed time:                 119.51 secs
Data transferred:               2.17 MB
Response time:                  0.02 secs
Transaction rate:              55.75 trans/sec
Throughput:                     0.02 MB/sec
Concurrency:                    0.98
Successful transactions:        6663
Failed transactions:               0
Longest transaction:            0.86
Shortest transaction:           0.00
```

- 배포기간 동안 Availability 가 변화없기 때문에 무정지 재배포가 성공한 것으로 확인됨.

## ConfigMap 사용
- 시스템별로 또는 운영중에 동적으로 변경 가능성이 있는 설정들을 ConfigMap을 사용하여 관리합니다.
- configmap.yaml
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: ssak3-config
  namespace: ssak3
data:
  api.url.payment: http://cleaner:8080
```

- reservation.yaml (configmap 사용)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cleanerregistration
  namespace: ssak3
  labels:
    app: cleanerregistration
spec:
  replicas: 1
  selector:
    matchLabels:
      app: cleanerregistration
  template:
    metadata:
      labels:
        app: cleanerregistration
    spec:
      containers:
        - name: cleanerregistration
          image: ssak3acr.azurecr.io/cleanerregistration:1.0
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
          env:
            - name: api.url.cleaner
              valueFrom:
                configMapKeyRef:
                  name: ssak3-config
                  key: api.url.cleaner
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
          livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5

---

apiVersion: v1
kind: Service
metadata:
  name: cleanerregistration
  namespace: ssak3
  labels:
    app: cleanerregistration
spec:
  ports:
    - port: 8080
      targetPort: 8080
  selector:
    app: cleanerregistration
```


```

