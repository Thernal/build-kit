# Builds, exports and tests one app in one flavor — the commands CI and a release run, in one place.
# An app is apps/<name>/ with three parts, the same for every app: android/ (the Android application
# module), ios/ (the Xcode project, from ios/project.yml) and shared/ (the Kotlin Multiplatform root
# both embed). APP picks all three.
#
#   make build-android APP=customer FLAVOR=beta BUILD_TYPE=release
#   make build-ios-framework APP=customer FLAVOR=prod
#   make test
#
# Flavors, the production flavor and the apps come from gradle.properties (app.*), as they do for the
# build. Store and Firebase distribution are not here: fastlane/Fastfile holds the store lanes, and
# Firebase App Distribution belongs to firebase-kit.

comma := ,
space := $(empty) $(empty)
prop = $(strip $(shell sed -n 's/^$(1)=//p' gradle.properties | tail -n1))
FLAVORS            := $(subst $(comma),$(space),$(call prop,app.flavors))
PRODUCTION_FLAVOR  := $(or $(call prop,app.flavors.production),$(lastword $(FLAVORS)))
MODULES_ROOT       := $(call prop,app.modules.root)
MODULE_PREFIX      := $(if $(MODULES_ROOT),:$(subst /,:,$(MODULES_ROOT)))
APPS               := $(notdir $(patsubst %/android/build.gradle.kts,%,$(wildcard $(if $(MODULES_ROOT),$(MODULES_ROOT)/)apps/*/android/build.gradle.kts)))

APP                  ?= $(firstword $(APPS))
FLAVOR               ?= $(or $(call prop,app.flavors.default),$(firstword $(FLAVORS)))
BUILD_TYPE           ?= release
# auto: an app bundle for production release, an APK for everything else.
ANDROID_ARTIFACT_TYPE ?= auto
ARTIFACT_PATH        ?= .misc/artifacts
GRADLE_ARGS          ?=
XCODEBUILD_ARGS      ?=

capitalize = $(shell echo "$(1)" | awk '{ print toupper(substr($$0,1,1)) substr($$0,2) }')
FLAVOR_CAP     := $(call capitalize,$(FLAVOR))
BUILD_TYPE_CAP := $(call capitalize,$(BUILD_TYPE))
APP_DIR        := $(if $(MODULES_ROOT),$(MODULES_ROOT)/)apps/$(APP)
APP_MODULE     := $(MODULE_PREFIX):apps:$(APP):android
ANDROID_DIR    := $(APP_DIR)/android
# The module whose iOS framework the app's Xcode project embeds, and that project.
IOS_FRAMEWORK_MODULE ?= $(MODULE_PREFIX):apps:$(APP):shared
IOS_DIR        := $(APP_DIR)/ios
XCODE_PROJECT  ?= $(IOS_DIR)/$(APP).xcodeproj
IOS_SCHEME     := $(APP)-$(FLAVOR_CAP)-$(BUILD_TYPE_CAP)
GRADLE         := ./gradlew -Papp.env=$(FLAVOR) $(GRADLE_ARGS)

ifeq ($(ANDROID_ARTIFACT_TYPE),auto)
ANDROID_ARTIFACT := $(if $(and $(filter $(PRODUCTION_FLAVOR),$(FLAVOR)),$(filter release,$(BUILD_TYPE))),aab,apk)
else
ANDROID_ARTIFACT := $(ANDROID_ARTIFACT_TYPE)
endif

ifeq (,$(filter $(FLAVOR),$(FLAVORS)))
$(error Unsupported FLAVOR "$(FLAVOR)". Use one of: $(FLAVORS))
endif
ifeq (,$(filter $(BUILD_TYPE),debug release))
$(error Unsupported BUILD_TYPE "$(BUILD_TYPE)". Use debug or release)
endif
ifeq (,$(filter $(APP),$(APPS)))
$(error Unsupported APP "$(APP)". Use one of: $(APPS))
endif

.PHONY: help build-android export-android verify-android-release bump-version-code \
        build-ios-framework build-ios build-ios-unsigned xcode-project test test-ios detekt graph

help:
	@echo "apps: $(APPS)   flavors: $(FLAVORS)   production: $(PRODUCTION_FLAVOR)"
	@echo "targets: build-android export-android verify-android-release bump-version-code"
	@echo "         build-ios-framework build-ios build-ios-unsigned test test-ios detekt graph"

# ─── Android ─────────────────────────────────────────────────────────────────

build-android:
ifeq ($(ANDROID_ARTIFACT),aab)
	$(GRADLE) $(APP_MODULE):bundle$(FLAVOR_CAP)$(BUILD_TYPE_CAP)
else
	$(GRADLE) $(APP_MODULE):assemble$(FLAVOR_CAP)$(BUILD_TYPE_CAP)
endif
	@$(MAKE) --no-print-directory export-android

export-android:
	@mkdir -p "$(ARTIFACT_PATH)/android"
ifeq ($(ANDROID_ARTIFACT),aab)
	cp -f $(ANDROID_DIR)/build/outputs/bundle/$(FLAVOR)$(BUILD_TYPE_CAP)/*.aab "$(ARTIFACT_PATH)/android/"
else
	cp -f $(ANDROID_DIR)/build/outputs/apk/$(FLAVOR)/$(BUILD_TYPE)/*.apk "$(ARTIFACT_PATH)/android/"
endif

# R8 must not break the release: builds it minified and checks the mapping file exists.
verify-android-release:
	$(GRADLE) $(APP_MODULE):assemble$(FLAVOR_CAP)Release
	test -f "$(ANDROID_DIR)/build/outputs/mapping/$(FLAVOR)Release/mapping.txt"
	@echo "OK: $(APP) $(FLAVOR) release minified; mapping at $(ANDROID_DIR)/build/outputs/mapping/$(FLAVOR)Release/"

bump-version-code:
	scripts/bump-version-code.sh $(APP) $(FLAVOR)

# ─── iOS ─────────────────────────────────────────────────────────────────────

build-ios-framework:
	$(GRADLE) $(IOS_FRAMEWORK_MODULE):link$(BUILD_TYPE_CAP)FrameworkIosArm64

# The .xcodeproj is generated from ios/project.yml (xcodegen) when the app keeps one there.
xcode-project:
	@if [ -f "$(IOS_DIR)/project.yml" ]; then cd "$(IOS_DIR)" && xcodegen generate --quiet; fi

# Xcode configurations and schemes are named after the flavor: "Beta Release", "<app>-Beta-Release".
# The framework build phase reads the flavor back from CONFIGURATION (see Flavors in the README).
build-ios: build-ios-framework xcode-project
	@mkdir -p "$(ARTIFACT_PATH)/ios"
	xcodebuild -project $(XCODE_PROJECT) -scheme "$(IOS_SCHEME)" \
	  -configuration "$(FLAVOR_CAP) $(BUILD_TYPE_CAP)" $(XCODEBUILD_ARGS) \
	  -archivePath "$(ARTIFACT_PATH)/ios/$(APP)-$(FLAVOR)-$(BUILD_TYPE).xcarchive" archive

build-ios-unsigned: xcode-project
	xcodebuild -project $(XCODE_PROJECT) -scheme "$(IOS_SCHEME)" \
	  -configuration "$(FLAVOR_CAP) $(BUILD_TYPE_CAP)" -sdk iphonesimulator \
	  -destination "generic/platform=iOS Simulator" CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO \
	  $(XCODEBUILD_ARGS) build

# ─── Checks ──────────────────────────────────────────────────────────────────

test:
	$(GRADLE) testAndroidHostTest test

test-ios:
	$(GRADLE) iosSimulatorArm64Test

detekt:
	./gradlew detektFull $(GRADLE_ARGS)

graph:
	./gradlew graph $(GRADLE_ARGS)
