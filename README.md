## 배치 구조 설계
각 feature 하나를 하나의 JOB으로 정의한다.

### JOB1. 이용권 만료
* `chunk step`
* 이용권 만료 대상을 읽어서(ExpirePassesReader) 그 대상들을 만료 상태로 업데이트(ExpirePassesWriter)

### JOB2. 이용권 일괄 지급
* `tasklet step`
* 어드민에 등록을 하게 되면 정해진 시간에 이용권이 사용자들에게 일괄 지급(AddPassesTasklet)
### JOB3. 예약 수업 전 알람
* `multiple thread chunk step`
    -> spring batch에서 제공하는 병렬 처리 방식
   1. Step1. 알람 대상을 가져오는 부분
   2. Step2. 알람을 전송하는 부분
###  JOB4. 수업 종료 후 이용권 차감
* `chunk step`
* UserPassesReader -> AsyncItemProcessor -> AsyncItemWriter
### JOB5. 통계 데이터 생성
1. Step1. 시간당 통계 데이터(`chunk step`)
	* StatisticReader, StatisticsWriter
2. Step2. 보고서에 추출(`tasklet step`)
	* DayStatisticsTasklet, WeakStatisticsTasklet
