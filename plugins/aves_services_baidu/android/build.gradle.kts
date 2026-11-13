group = "deckers.thibault.aves.services.baidu"
version = "1.0-SNAPSHOT"

plugins {
    id("com.android.library")
}

android {
    namespace = "deckers.thibault.aves.services.baidu"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.baidu.lbsyun:BaiduMapSDK_Map:8.2.0")
    implementation("com.baidu.lbsyun:BaiduMapSDK_Util:8.2.0")
}
