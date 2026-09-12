import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Color

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tvparapobres.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tvparapobres.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 14
        versionName = "1.3.5"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "tvparapobres123"
            keyAlias = "tvparapobres"
            keyPassword = "tvparapobres123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.19.1")
}

// Genera banner (logo horizontal) e iconos circular/legacy (logo cuadrado)
// a partir de los logos del proyecto.
tasks.register("generateBrandAssets") {
    val logoWide = file("src/main/res/drawable-nodpi/logo_proyecto.png")
    val logoSquare = file("../../assets/icon.png")
    inputs.file(logoWide)
    inputs.file(logoSquare)
    outputs.file("src/main/res/drawable-nodpi/banner.png")
    outputs.file("src/main/res/drawable-nodpi/ic_fg_logo.png")
    outputs.file("src/main/res/mipmap-anydpi/ic_launcher.png")
    doLast {
        val wide = ImageIO.read(logoWide)
        val square = if (logoSquare.exists()) ImageIO.read(logoSquare) else wide
        fun compose(src: BufferedImage, targetW: Int, targetH: Int, contentW: Int): BufferedImage {
            val canvas = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB)
            val g: Graphics2D = canvas.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(6, 6, 8)
            g.fillRect(0, 0, targetW, targetH)
            val aspect = src.width.toDouble() / src.height.toDouble()
            var cw = contentW
            var ch = (cw / aspect).toInt()
            if (ch > targetH - 6) {
                ch = targetH - 6
                cw = (ch * aspect).toInt()
            }
            val x = (targetW - cw) / 2
            val y = (targetH - ch) / 2
            g.drawImage(src, x, y, x + cw, y + ch, 0, 0, src.width, src.height, null)
            g.dispose()
            return canvas
        }
        ImageIO.write(compose(wide, 320, 180, 292), "png", file("src/main/res/drawable-nodpi/banner.png"))
        ImageIO.write(compose(square, 1024, 1024, 860), "png", file("src/main/res/drawable-nodpi/ic_fg_logo.png"))
        ImageIO.write(compose(square, 432, 432, 420), "png", file("src/main/res/mipmap-anydpi/ic_launcher.png"))
    }
}
tasks.named("preBuild") { dependsOn("generateBrandAssets") }