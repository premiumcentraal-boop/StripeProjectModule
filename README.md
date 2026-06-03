# Project Stripe

Project Stripe is a lightweight Android manager APK for checking and controlling a local Vector-style module setup.

It uses the local Vector framework tree as reference context, but does not rebuild or modify Vector. The APK provides:

- Project Stripe and Vector status
- Root, Magisk, and Zygisk checks
- Vector/LSPosed module folder detection under `/data/adb/modules`
- Installed manager package detection for `vector`, `lsposed`, and `libxposed`
- Setup guidance, tools, logs, and settings screens

## Package

`com.projectstripe.manager`

## Latest APK

After the GitHub workflow succeeds, the stable download URL is:

```text
https://github.com/premiumcentraal-boop/StripeProjectModule/releases/download/project-stripe-latest/project-stripe-debug.apk
```

## Build Locally

Requires a local JDK 17, Android SDK, and Gradle 8.7:

```bash
gradle :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build In GitHub

Run:

```text
Actions -> Build Project Stripe APK -> Run workflow
```

The workflow uploads:

```text
project-stripe-debug-apk
```

It also publishes the latest APK to the `project-stripe-latest` prerelease.

## Safety

Project Stripe does not install Magisk modules, does not enable scopes, and does not modify external app settings. It only provides UI, local setup checks, logs, and configuration visibility.
