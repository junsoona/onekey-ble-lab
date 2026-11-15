# OneKey BLE Lab

Android 스마트폰으로 테스트용 BLE Legacy 광고를 송신하고, 수신기에서 패킷과 백그라운드 동작을 확인하는 앱입니다. 패키지는 `com.onekey.blelab`입니다. 관찰한 리모컨의 Manufacturer Specific Data 구조를 참고했습니다. 제조사 ID만으로 제품 공급사를 특정하거나, 패킷 수신으로 공동현관 인증 여부를 판정할 수 없습니다. 향후 출입 연동에는 현관 시스템 공급사가 승인한 모바일 자격증명이나 SDK가 필요합니다.

## 설치와 빌드

- Debug APK는 [app/build/outputs/apk/debug/app-debug.apk](app/build/outputs/apk/debug/app-debug.apk)에 있습니다.
- Android Studio에서는 `settings.gradle.kts`가 있는 프로젝트 루트를 엽니다. AGP 9.5와 호환되는 Studio에서 Gradle JDK 17 이상을 선택하고 SDK Manager에서 Android SDK Platform 37.0, Build-Tools 37.0.0, Platform-Tools를 설치합니다. 프로젝트는 Gradle Wrapper 9.6.0과 AGP 9.5.1을 사용합니다. `compileSdk` 및 `targetSdk`는 37이고 최소 버전은 Android 12(API 31)입니다. [Android 17 SDK](https://developer.android.com/about/versions/17/setup-sdk), [AGP 9.5](https://developer.android.com/build/releases/agp-9-5-0-release-notes)
- 터미널에서는 JDK의 `JAVA_HOME`과 SDK의 `ANDROID_HOME`을 설정하거나, SDK 경로를 프로젝트의 `local.properties`에 `sdk.dir=절대경로`로 지정합니다. 이번 Mac 작업 폴더의 도구를 재사용하려면 프로젝트 루트에서 Bash로 `source ../../work/build-env.sh`를 실행합니다.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb devices -l
adb -d install -r app/build/outputs/apk/debug/app-debug.apk
adb -d shell am start -n com.onekey.blelab/.MainActivity
```

유선 ADB 설치 시 Galaxy의 개발자 옵션에서 USB 디버깅을 켜고 Mac의 연결 요청을 승인합니다. `adb devices -l`에 상태가 `device`로 표시되는지 확인합니다.

## Android 17에서 송신하기

1. 앱의 **송신** 탭에서 **주변 기기 권한 허용**을 누릅니다. Android는 `BLUETOOTH_ADVERTISE`와 `BLUETOOTH_CONNECT`를 주변 기기 권한으로 표시합니다. 요청을 다시 열 수 없으면 **앱 설정 열기 → 권한 → 주변 기기**에서 허용합니다. [Android Bluetooth 권한](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
2. Bluetooth를 켜고 **BLE 광고** 상태가 **지원**인지 확인합니다. **송신 알림 허용**으로 `POST_NOTIFICATIONS`를 허용하면 알림에서 송신 상태와 **광고 중지** 버튼을 볼 수 있습니다. 알림을 거부해도 Android는 서비스 시작을 허용하며 시스템의 활성 앱 목록에서 서비스를 표시합니다. 앱은 `FOREGROUND_SERVICE`와 `FOREGROUND_SERVICE_CONNECTED_DEVICE`를 선언하고 위치, 인터넷, 저장소 권한 없이 동작합니다. [포그라운드 서비스 유형](https://developer.android.com/develop/background-work/services/fgs/service-types), [알림 권한](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
3. 기본 테스트 송신은 **광고 시작**을 누릅니다. 기본 패킷은 Manufacturer ID `0xFFFF`, 데이터 `4F 4E 45 4B 45 59 2D 4C 41 42 01`, 이름 생략, 약 1,000ms 절전 모드, TX 낮음입니다.
4. 이름을 넣으려면 중지 상태에서 `OneKey-Lab` 같은 이름을 입력합니다. 현재 Bluetooth 이름과 다르면 **기기 이름 적용**을 누르고 기기 전체 Bluetooth 이름 변경을 확인합니다. 입력값과 실제 어댑터 이름이 일치하면 시작 버튼이 활성화됩니다. 이름 변경은 송신을 중지한 뒤에도 유지됩니다. 원래 이름은 Bluetooth 설정이나 기기 이름 설정에서 다시 지정하세요. 기기 이름 변경이 거부되면 시스템 설정에서 이름을 직접 바꾸거나 Local Name을 비워 재시도할 수 있습니다. [BluetoothAdapter.setName](https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#setName(java.lang.String)), [AdvertiseData.Builder](https://developer.android.com/reference/android/bluetooth/le/AdvertiseData.Builder)
5. 설정은 **저장**할 때 DataStore에 기록되고 **불러오기** 또는 앱을 다시 열 때 읽힙니다. **광고 시작**은 화면에 입력한 값을 사용합니다. 부팅 옵션은 저장된 프로필을 사용합니다. **진단**에서 시작 콜백, 마지막 오류, 시작 및 중지 요청 시각, 송신 설정과 로그를 확인할 수 있습니다. 로그는 프로세스당 최근 100개이며 **복사** 또는 **내보내기**로 보존할 수 있습니다. 내보내기는 Android 파일 선택기를 열어 UTF-8 텍스트를 저장합니다.

## Legacy 패킷 제약

- 기본 광고 패킷(ADV)은 Manufacturer Specific Data AD 구조 하나를 포함합니다. 데이터 입력란에 Manufacturer ID를 붙이지 마세요. 플랫폼이 AD 길이, 타입 `FF`, 16비트 ID의 리틀엔디언 두 바이트를 넣습니다. 예를 들어 ID `0059`는 전파 상에서 `59 00`입니다. 기본 패킷의 예상 AD 바이트열은 `0E FF FF FF 4F 4E 45 4B 45 59 2D 4C 41 42 01`입니다.
- ADV와 Scan Response의 예산은 각각 31바이트입니다. 비연결형 광고를 사용하므로 현재 AOSP의 Legacy API 경로에서 ADV에 자동 Flags 필드를 예약하지 않습니다. Manufacturer Data는 헤더와 ID 4바이트를 제외한 최대 27바이트입니다. Local Name은 별도 Scan Response에 들어갑니다. 이름 필드의 헤더는 2바이트이고, Android가 긴 이름을 잘라 Shortened Local Name을 생성하는 동작을 피하기 위해 입력은 UTF-8 최대 26바이트로 제한했습니다. Service UUID와 TX Power AD 필드는 설정하지 않습니다. [Android Legacy 광고 API](https://developer.android.com/reference/android/bluetooth/le/BluetoothLeAdvertiser), [AOSP 광고 크기 산정](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/framework/java/android/bluetooth/le/BluetoothLeAdvertiser.java), [AOSP AD 바이트 생성](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/android/app/src/com/android/bluetooth/gatt/AdvertiseHelper.java)
- `AdvertiseSettings`의 세 모드는 절전 약 1,000ms, 균형 약 250ms, 저지연 약 100ms입니다. 이 버전은 모드 선택을 제공하며 임의의 500ms 주기를 지정하는 기능은 없습니다. TX Power는 네 단계의 요청값입니다. 실제 주기, 송신 강도와 주소는 Android 및 컨트롤러가 결정합니다. 수신기의 RSSI는 환경과 거리에 따라 달라집니다. [AdvertiseSettings](https://developer.android.com/reference/android/bluetooth/le/AdvertiseSettings), [AOSP 모드 매핑](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/framework/java/android/bluetooth/le/BluetoothLeAdvertiser.java)
- 시작 성공은 Android가 광고 시작을 수락했다는 뜻입니다. Legacy API의 중지는 완료 콜백을 제공하지 않으므로 앱에는 중지 요청 시각을 기록합니다. 송신과 중지 모두 수신기의 새 관측으로 검증하세요.

## Galaxy S24+ 화면 OFF와 백그라운드

처음에는 One UI 기본 설정에서 측정합니다. 광고가 끊기는 경우 다음 설정을 적용하고 같은 조건에서 다시 측정합니다. One UI 버전과 기기 언어에 따라 메뉴 이름이 다를 수 있습니다. [Samsung 백그라운드 사용 제한](https://www.samsung.com/us/support/answer/ANS10002534/), [Samsung 앱 배터리 설정](https://www.samsung.com/us/support/answer/ANS10001538/)

1. **설정 → 앱 → OneKey BLE Lab → 배터리 → 제한 없음**을 선택합니다.
2. **설정 → 배터리 → 백그라운드 사용 제한**에서 앱을 절전 및 초절전 목록에서 제거합니다. 필요하면 **절전 예외 앱** 또는 **Never auto sleeping apps**에 추가합니다.
3. 기본 비교는 시스템 절전 모드를 끈 상태에서 진행하고 이후 절전 모드를 켠 상태도 별도로 기록합니다. 설정 변경에 따른 배터리 차이는 Galaxy의 앱 배터리 사용량 화면에서 확인합니다.

서비스는 시작 시 `connectedDevice` 유형으로 승격되며 홈 화면 이동, 최근 앱에서 화면 제거, 화면 잠금과 별도로 실행됩니다. Bluetooth가 꺼지면 현재 세션은 재활성화 대기로 바뀝니다. 서비스가 살아 있을 때 Bluetooth가 다시 켜지면 같은 설정으로 한 번 시작을 시도합니다. 오류나 종료 이후에는 앱에서 수동으로 다시 시작할 수 있습니다. 프로세스가 종료되면 `START_NOT_STICKY`이므로 OS에 자동 재실행을 요청하지 않습니다. [포그라운드 서비스 시작 정책](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [활성 앱 목록에서 중지](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)

**재부팅 후 자동 시작**은 기본 꺼짐입니다. 켜고 저장하면 첫 잠금 해제 후 앱에 `BOOT_COMPLETED`가 전달될 때 저장한 설정으로 시작을 시도합니다. 주변 기기 권한이 있고 Bluetooth가 켜져 있거나 켜지는 중이어야 합니다. 이름이 있는 프로필은 저장된 이름과 기기 이름이 일치해야 합니다. Android의 백그라운드 제한, 강제 중지 상태, One UI 정책에 따라 부팅 방송 전달이나 서비스 시작이 생략 또는 거부될 수 있습니다. 자동 시작이 생략되면 앱에서 시작합니다. 수동으로 광고를 중지해도 저장된 부팅 옵션은 켜져 있으므로 다음 부팅에서도 시도합니다. 해제하려면 스위치를 끄고 저장합니다. [Android의 부팅 및 서비스 시작 제한](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)

## nRF Connect 수신 검증

1. 송신 Galaxy와 별개인 스마트폰에서 **nRF Connect for Mobile → Scanner → Scan**을 엽니다. 수신기의 Bluetooth와 스캔 권한을 켭니다. [Nordic nRF Connect for Mobile](https://www.nordicsemi.com/Products/Development-tools/nRF-Connect-for-mobile)
2. Galaxy에서 기본 송신을 시작합니다. 수신기에서 광고 항목을 펼쳐 Manufacturer ID `FFFF`와 데이터 `4F 4E 45 4B 45 59 2D 4C 41 42 01`을 확인합니다. 이름이 없는 기본 패킷은 Manufacturer Data로 식별하고, 원치 않는 캐시 결과를 피하려면 스캔 목록을 지운 뒤 다시 스캔하세요.
3. 이름을 설정한 송신도 반복합니다. Scan Response를 받는 스캔에서 광고 데이터 내부의 **Complete Local Name**을 확인하세요. 스캔 목록의 제목에는 캐시된 기기 이름이 표시될 수 있습니다. 수신 앱에서 형식이 노출되면 Legacy 및 비연결형 여부를 기록합니다.
4. 고정 거리에서 RSSI와 같은 패킷의 수신 시각을 여러 번 기록합니다. 3개 모드를 차례로 송신해 관찰 간격을 비교합니다. 스캔 측 중복 필터, 배치 및 스캔 창이 결과에 영향을 주므로 관찰 간격은 무선 컨트롤러의 정확한 광고 주기를 직접 재는 값과 차이가 날 수 있습니다.
5. Galaxy의 홈 이동, 다른 앱 전환, 최근 앱에서 화면 제거, 화면 잠금 및 화면 OFF 뒤 수신기에서 새 패킷이 도착하는지 확인합니다. 화면 OFF 상태로 30분, 60분에 각각 도착 시각과 RSSI를 확인하고 수신 공백, Galaxy 배터리 퍼센트와 발열 변화를 기록합니다. 송신 중지 상태의 같은 시간 구간을 배터리 비교 기준으로 사용합니다.
6. 송신 중 Bluetooth OFF에서 새 패킷이 멈추고 앱이 대기 상태로 바뀌는지 확인합니다. ON으로 바꾼 뒤 성공 콜백과 새 패킷을 확인합니다. 앱과 알림의 **광고 중지**도 각각 시험합니다. 스캔 목록에 남은 항목과 새로 수신된 패킷을 구분하세요.
7. `adb -d shell cmd activity stop-app com.onekey.blelab` 또는 Android의 활성 앱 목록에서 중지를 실행합니다. 새 패킷 중단과 앱 재실행 시 대기 상태를 확인합니다. 재부팅 자동 시작은 옵션을 끈 상태와 켜서 저장한 상태를 각각 시험합니다. [Android 활성 앱 중지 테스트](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)

## 프로젝트 구성

- `MainActivity`, `LabScreen`, `AdvertiserViewModel`에서 Compose Material 3 화면, 다크 모드, 권한 요청, 입력 및 저장 작업을 관리합니다.
- `AdvertisingConfiguration`, `PermissionManager`, `BleAdvertiserManager`에서 패킷 검증, 기기 상태 조회, Legacy API 콜백과 광고 수명을 처리합니다.
- `BleForegroundService`, `BootReceiver`, `SettingsRepository`, `DiagnosticLogger`에서 백그라운드 실행, 부팅 옵션, DataStore 저장, 세션 상태 및 로그를 처리합니다.
- `app/src/test`에 HEX, UTF-8 이름과 패킷 크기 경계, 광고 모드, Android 콜백 오류 코드 단위 테스트가 있습니다.

## 검증 결과

2026-11-16 KST · Apple Silicon macOS 27.0.1, Zulu JDK 21, Android SDK Platform 37.0, Build-Tools 37.0.0에서 확인했습니다.

- `:app:assembleDebug` 성공. APK 메타데이터에서 `com.onekey.blelab`, Android 17 대상(API 37), 최소 API 31, 설치용 시작 액티비티, ARM64 지원을 확인했습니다.
- `:app:testDebugUnitTest` 성공. 11개 통과, 실패와 건너뜀 0개입니다.
- `:app:lintDebug` 성공. 오류 0개, Gradle 버전 권고 1개입니다. Wrapper는 AGP 9.5의 공식 기본 버전인 9.6.0을 사용합니다.
- `apksigner verify`와 `zipalign -c -P 16 4`를 통과했습니다. APK SHA-256은 `e92998db65687e8251c5cfa9593125e2ed7c8027ae9c63f923f73eab34ee2fbd`입니다.
- 사용자가 Galaxy S24+에서 APK 설치 성공을 확인했습니다.
- Mac 검증 중 ADB로 연결된 기기는 없었습니다. 실제 무선 패킷 수신, Bluetooth OFF/ON 복구, 부팅, 화면 OFF 30분 및 60분 유지, 배터리와 발열은 미검증입니다.
