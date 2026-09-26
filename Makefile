# mdwriter - build, sign and install on your Android phone.
#
#   make                 show this help
#   make install         build the release app and install/upgrade it on the phone (keeps notes), then launch
#
# Written for GNU make 3.81 (the macOS default /usr/bin/make): no .ONESHELL, $(file), !=, ::=, undefine.
# Every multi-line recipe is ONE bash invocation joined with "; \".

SHELL := /bin/bash
.DEFAULT_GOAL := help
.SUFFIXES:
.NOTPARALLEL:

# ------------------------------------------------------------------ project
MODULE := app
# applicationId is read from app/build.gradle.kts (single source of truth).
APP_ID := $(shell sed -n 's/^[[:space:]]*applicationId[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' $(MODULE)/build.gradle.kts 2>/dev/null | head -n 1)
ifeq ($(APP_ID),)
APP_ID := dev.mdwriter
endif
APP_ID_DEBUG := $(APP_ID).debug
RELEASE_APK  := $(MODULE)/build/outputs/apk/release/$(MODULE)-release.apk
DEBUG_APK    := $(MODULE)/build/outputs/apk/debug/$(MODULE)-debug.apk
MIN_API      := 36
AVD          ?= Pixel_10_Pro_XL

# ------------------------------------------------------------------ Android SDK
# Same precedence as Gradle: local.properties sdk.dir > ANDROID_HOME > ANDROID_SDK_ROOT > OS default.
ifeq ($(shell uname -s),Darwin)
DEFAULT_SDK := $(HOME)/Library/Android/sdk
else
DEFAULT_SDK := $(HOME)/Android/Sdk
endif
LOCAL_SDK := $(shell sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null | head -n 1)
SDK := $(or $(LOCAL_SDK),$(ANDROID_HOME),$(ANDROID_SDK_ROOT),$(DEFAULT_SDK))
export ANDROID_HOME := $(SDK)

ADB_BIN      := $(SDK)/platform-tools/adb
EMULATOR_BIN := $(SDK)/emulator/emulator
BUILD_TOOLS  := $(SDK)/build-tools/36.0.0

# DEVICE=<serial> picks the phone when several are connected (defaults to $ANDROID_SERIAL).
DEVICE ?= $(ANDROID_SERIAL)

# ------------------------------------------------------------------ JDK
# Gradle 9.7 must be LAUNCHED by a JDK 17..26 (Homebrew's unversioned `openjdk` is 27 -> fails).
# First usable candidate wins: $JAVA_HOME, JDK 21 from java_home, Android Studio's JBR, JDK 17, Homebrew, Linux.
JDK_MIN := 17
JDK_MAX := 26
define FIND_JDK
jmajor() { v=$$(sed -n 's/^JAVA_VERSION="\([^"]*\)".*/\1/p' "$$1/release" 2>/dev/null); \
  [ -n "$$v" ] || v=$$("$$1/bin/java" -version 2>&1 | awk -F'"' '/version/ {print $$2; exit}'); \
  case "$$v" in (1.*) v=$${v:2};; esac; echo "$${v%%[!0-9]*}"; }; \
for c in "$$JAVA_HOME" \
  "$$(/usr/libexec/java_home -v 21 --failfast 2>/dev/null)" \
  "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  "$$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  "$$(/usr/libexec/java_home -v 17 --failfast 2>/dev/null)" \
  /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  /opt/android-studio/jbr "$$HOME/android-studio/jbr" \
  /usr/lib/jvm/java-21-openjdk* /usr/lib/jvm/java-17-openjdk*; do \
  [ -n "$$c" ] && [ -x "$$c/bin/java" ] || continue; \
  m=$$(jmajor "$$c"); \
  if [ -n "$$m" ] && [ "$$m" -ge $(JDK_MIN) ] && [ "$$m" -le $(JDK_MAX) ]; then echo "$$c"; exit 0; fi; \
done
endef
ENV_JAVA_HOME := $(JAVA_HOME)
JDK := $(shell $(FIND_JDK))
export JAVA_HOME := $(JDK)

# ------------------------------------------------------------------ signing
# The release key lives OUTSIDE the repo, so `git clean -fdx` or a fresh clone can never lose it.
# app/build.gradle.kts looks in the same default place, so Android Studio builds use the same key.
KEYSTORE_DIR   ?= $(HOME)/.config/mdwriter
KEYSTORE_FILE  := $(KEYSTORE_DIR)/release.jks
KEYSTORE_PROPS := $(KEYSTORE_DIR)/keystore.properties
KEY_ALIAS      := mdwriter

# ------------------------------------------------------------------ gradle
# VERSION_CODE=<int> overrides the build-time versionCode (normally never needed).
GRADLEW      := ./gradlew
GRADLE_FLAGS ?=
GRADLE       := $(GRADLEW) $(GRADLE_FLAGS) -Pmdwriter.keystore="$(KEYSTORE_PROPS)"$(if $(VERSION_CODE), -Pmdwriter.versionCode=$(VERSION_CODE))

# Colours only on a terminal ($(shell) captures stdout, so test stderr) and unless NO_COLOR is set.
ifeq ($(NO_COLOR)$(shell [ -t 2 ] && echo tty),tty)
B := \033[1m
R := \033[31m
G := \033[32m
Y := \033[33m
N := \033[0m
endif

# ------------------------------------------------------------------ shell fragments
# PICK_DEVICE: sets $adb and $serial (DEVICE=..., else the single connected device), checks that the
# device is authorized and runs Android 16+ (API 36), and exports ANDROID_SERIAL so Gradle's install
# task only touches that device. Exits with instructions otherwise.
define PICK_DEVICE
adb="$(ADB_BIN)"; \
[ -x "$$adb" ] || { printf "$(R)ERROR:$(N) adb not found at $$adb (run: make doctor)\n"; exit 1; }; \
serial="$(DEVICE)"; \
if [ -z "$$serial" ]; then \
  list=$$("$$adb" devices | awk 'NR>1 && NF>=2 {print $$1" "$$2}'); \
  ready=$$(printf '%s\n' "$$list" | awk '$$2=="device" {print $$1}'); \
  n=$$(printf '%s\n' "$$ready" | grep -c .); \
  if printf '%s\n' "$$list" | grep -q ' unauthorized$$'; then \
    printf "$(Y)WARNING:$(N) a device is 'unauthorized': unlock the phone and accept 'Allow USB debugging?'\n"; \
    printf "         (tick 'Always allow from this computer'). If no prompt appears: Developer options >\n"; \
    printf "         Revoke USB debugging authorizations, then unplug/replug.\n"; fi; \
  if printf '%s\n' "$$list" | grep -q ' offline$$'; then \
    printf "$(Y)WARNING:$(N) a device is 'offline': unplug/replug it, or run: $$adb kill-server\n"; fi; \
  if [ "$$n" -eq 0 ]; then \
    printf "$(R)ERROR:$(N) no Android device connected.\n"; \
    printf "  USB  : phone Settings > System > Developer options > USB debugging ON, plug in, accept the prompt\n"; \
    printf "  Wi-Fi: Developer options > Wireless debugging > Pair device with pairing code, then\n"; \
    printf "         make pair HOST=<ip:port> CODE=<code>   and   make connect HOST=<ip:port>\n"; \
    printf "  Emulator: make emulator\n"; \
    printf "  (README.md, section 'Install on your phone', has the step-by-step guide)\n"; exit 1; \
  elif [ "$$n" -gt 1 ]; then \
    printf "$(R)ERROR:$(N) $$n devices connected - choose one: make $@ DEVICE=<serial>\n"; \
    "$$adb" devices -l | sed 1d | sed '/^$$/d; s/^/  /'; exit 1; \
  fi; \
  serial="$$ready"; \
fi; \
st=$$("$$adb" -s "$$serial" get-state 2>/dev/null); \
[ "$$st" = "device" ] || { printf "$(R)ERROR:$(N) device $$serial is not ready (state: $${st:-not connected})\n"; "$$adb" devices -l; exit 1; }; \
api=$$("$$adb" -s "$$serial" shell getprop ro.build.version.sdk | tr -d '\r'); \
rel=$$("$$adb" -s "$$serial" shell getprop ro.build.version.release | tr -d '\r'); \
model=$$("$$adb" -s "$$serial" shell getprop ro.product.model | tr -d '\r'); \
if [ -n "$$api" ] && [ "$$api" -lt $(MIN_API) ]; then \
  printf "$(R)ERROR:$(N) $$model ($$serial) runs Android $$rel (API $$api). mdwriter needs Android 16 (API $(MIN_API)) or newer.\n"; exit 1; fi; \
export ANDROID_SERIAL="$$serial"
endef

# EXPLAIN_INSTALL_FAILURE: turns known PackageManager errors in $log into advice. Never uninstalls:
# a failed install leaves the installed app AND its data untouched.
define EXPLAIN_INSTALL_FAILURE
if grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' "$$log"; then \
  printf "\n$(R)NOT INSTALLED: the app on the phone was signed with a DIFFERENT key.$(N) Your notes on the phone are untouched.\n"; \
  printf "  Fix: restore the original key files into $(KEYSTORE_DIR)/ from your backup, then: make install\n"; \
  printf "  Do NOT uninstall unless you exported your notes first (make uninstall deletes them).\n"; \
elif grep -q 'INSTALL_FAILED_VERSION_DOWNGRADE' "$$log"; then \
  printf "\n$(R)NOT INSTALLED: the phone has a newer versionCode.$(N) Check this Mac's clock, or: make install VERSION_CODE=<bigger>\n"; \
elif grep -q -e 'INSTALL_FAILED_OLDER_SDK' -e 'minSdk' "$$log"; then \
  printf "\n$(R)NOT INSTALLED: the phone runs an Android version older than 16 (API $(MIN_API)).$(N)\n"; \
elif grep -q 'INSTALL_FAILED_INSUFFICIENT_STORAGE' "$$log"; then \
  printf "\n$(R)NOT INSTALLED: the phone is out of storage.$(N)\n"; \
elif grep -q -e 'INSTALL_FAILED_USER_RESTRICTED' -e 'INSTALL_FAILED_ABORTED' "$$log"; then \
  printf "\n$(R)NOT INSTALLED: the phone rejected the install.$(N) Watch its screen and accept the prompt\n"; \
  printf "  (Xiaomi/Oppo/Vivo: also enable Developer options > 'Install via USB').\n"; \
elif grep -q -e 'INSTALL_PARSE_FAILED_NO_CERTIFICATES' -e 'INSTALL_PARSE_FAILED' "$$log"; then \
  printf "\n$(R)NOT INSTALLED: the APK is unsigned or corrupt.$(N) Run: make clean install\n"; \
fi
endef

# LAUNCH: starts $(PKG) through its launcher activity (works for release and .debug).
# (An implicit MAIN/LAUNCHER "am start -p" does not resolve - no DEFAULT category - so resolve first.)
define LAUNCH
comp=$$("$$adb" -s "$$serial" shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER "$(PKG)" | tr -d '\r' | tail -n 1); \
case "$$comp" in */*) ;; *) printf "$(R)ERROR:$(N) $(PKG) is not installed on $$serial (run: make install or make install-debug)\n"; exit 1;; esac; \
"$$adb" -s "$$serial" shell am start -W -n "$$comp" | tr -d '\r' | grep -q '^Status: ok' \
  && printf "$(G)Launched$(N) $$comp on $$model ($$serial)\n" \
  || { printf "$(R)ERROR:$(N) could not launch $$comp\n"; exit 1; }
endef

# GRADLE_INSTALL: runs Gradle's install task $(TASK) (APK + baseline-profile .dm), hides the
# "> Task" noise, keeps the full log for EXPLAIN_INSTALL_FAILURE.
define GRADLE_INSTALL
printf "Building and installing $(B)$(PKG)$(N) on $$model ($$serial, Android $$rel) ...\n"; \
log=$$(mktemp "$${TMPDIR:-/tmp}/mdwriter-install.XXXXXX"); \
$(GRADLE) --console=plain $(TASK) 2>&1 | tee "$$log" | grep --line-buffered -v -e '^> Task ' -e '^$$'; \
rc=$${PIPESTATUS[0]}; \
if [ "$$rc" -ne 0 ]; then $(EXPLAIN_INSTALL_FAILURE); rm -f "$$log"; exit "$$rc"; fi; \
rm -f "$$log"; \
ver=$$("$$adb" -s "$$serial" shell dumpsys package "$(PKG)" | tr -d '\r' | sed -n 's/^ *versionName=//p' | head -n 1); \
code=$$("$$adb" -s "$$serial" shell dumpsys package "$(PKG)" | tr -d '\r' | sed -n 's/^ *versionCode=\([0-9]*\).*/\1/p' | head -n 1); \
printf "$(G)Installed$(N) $(PKG) $$ver (versionCode $$code)\n"
endef

.PHONY: help install install-debug run run-debug apk debug-apk test lint format check clean \
        keystore keystore-info devices pair connect emulator logcat uninstall uninstall-debug \
        backup-notes doctor env-check test-device

# ================================================================== targets

help: ## Show this help
	@printf "$(B)mdwriter$(N) - make targets\n\n"
	@grep -E '^[a-zA-Z][a-zA-Z_-]*:.*## ' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*## "} {printf "  $(G)%-15s$(N) %s\n", $$1, $$2}'
	@printf "\nVariables: DEVICE=<serial>  HOST=<ip:port>  CODE=<pairing code>  AVD=<name>  CONFIRM=yes\n"
	@printf "First time? Enable USB or Wireless debugging on the phone (see README), then: make doctor && make install\n"

install: env-check keystore ## Build the release app, install/upgrade it on the phone (keeps notes), launch it
	@$(PICK_DEVICE); \
	$(call GRADLE_INSTALL); \
	$(call LAUNCH)
install: TASK := :$(MODULE):installRelease
install: PKG := $(APP_ID)

install-debug: env-check ## Build + install + launch the debug app (separate ".debug" app with its own notes)
	@$(PICK_DEVICE); \
	$(call GRADLE_INSTALL); \
	$(call LAUNCH)
install-debug: TASK := :$(MODULE):installDebug
install-debug: PKG := $(APP_ID_DEBUG)

run: ## Launch the installed release app (no build)
	@$(PICK_DEVICE); $(call LAUNCH)
run: PKG := $(APP_ID)

run-debug: ## Launch the installed debug app (no build)
	@$(PICK_DEVICE); $(call LAUNCH)
run-debug: PKG := $(APP_ID_DEBUG)

apk: env-check keystore ## Build the signed, optimized release APK without installing; prints its path
	@$(GRADLE) :$(MODULE):assembleRelease
	@printf "$(G)APK:$(N) %s (%s)\n" "$(CURDIR)/$(RELEASE_APK)" "$$(du -h "$(RELEASE_APK)" | cut -f1 | tr -d ' ')"
	@[ ! -x "$(BUILD_TOOLS)/apksigner" ] || "$(BUILD_TOOLS)/apksigner" verify --print-certs "$(RELEASE_APK)" | grep -m 2 -e 'DN:' -e 'SHA-256'

debug-apk: env-check ## Build the debug APK without installing; prints its path
	@$(GRADLE) :$(MODULE):assembleDebug
	@printf "$(G)APK:$(N) %s\n" "$(CURDIR)/$(DEBUG_APK)"

test: env-check ## Run all JVM unit tests (every module)
	@$(GRADLE) test

test-device: env-check ## Run instrumented tests on the connected device/emulator (debug build)
	@$(PICK_DEVICE); \
	$(GRADLE) --console=plain :$(MODULE):connectedDebugAndroidTest

lint: env-check ## Run Android Lint (report: app/build/reports/lint-results-debug.html)
	@$(GRADLE) lint

format: env-check ## Auto-format Kotlin + Gradle files (Spotless + ktlint)
	@$(GRADLE) spotlessApply

check: env-check ## What CI runs: format check, lint, unit tests, release build (R8)
	@$(GRADLE) spotlessCheck lint test :$(MODULE):assembleRelease

clean: ## Delete build outputs (never touches the signing key or the phone)
	@if [ -x "$(GRADLEW)" ] && [ -n "$(JDK)" ]; then $(GRADLEW) --quiet clean; else rm -rf build */build; fi
	@rm -rf .kotlin
	@printf "Cleaned.\n"

keystore: ## Create the release signing key once (in ~/.config/mdwriter) - BACK IT UP
	@if [ -f "$(KEYSTORE_PROPS)" ] && [ -f "$(KEYSTORE_FILE)" ]; then exit 0; fi; \
	if [ -f "$(KEYSTORE_FILE)" ] || [ -f "$(KEYSTORE_PROPS)" ]; then \
	  printf "$(R)ERROR:$(N) only one of these exists:\n  $(KEYSTORE_FILE)\n  $(KEYSTORE_PROPS)\n"; \
	  printf "Restore the missing file from your backup. Do NOT create a new key: a phone that has the app\n"; \
	  printf "would refuse the update, and the only way out would be uninstalling (= deleting the notes).\n"; \
	  exit 1; \
	fi; \
	[ -n "$(JDK)" ] || { printf "$(R)ERROR:$(N) no JDK $(JDK_MIN)-$(JDK_MAX) found (run: make doctor)\n"; exit 1; }; \
	set -e; umask 077; mkdir -p "$(KEYSTORE_DIR)"; chmod 700 "$(KEYSTORE_DIR)"; \
	pw=$$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32); \
	MDW_KS_PW="$$pw" "$$JAVA_HOME/bin/keytool" -genkeypair -noprompt \
	  -keystore "$(KEYSTORE_FILE)" -storetype PKCS12 -alias "$(KEY_ALIAS)" \
	  -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=mdwriter" \
	  -storepass:env MDW_KS_PW -keypass:env MDW_KS_PW; \
	printf 'storeFile=release.jks\nstorePassword=%s\nkeyAlias=%s\nkeyPassword=%s\n' \
	  "$$pw" "$(KEY_ALIAS)" "$$pw" > "$(KEYSTORE_PROPS)"; \
	chmod 600 "$(KEYSTORE_FILE)" "$(KEYSTORE_PROPS)"; \
	printf "\n$(G)Created the release signing key:$(N)\n  $(KEYSTORE_FILE)\n  $(KEYSTORE_PROPS)\n"; \
	printf "$(Y)BACK UP BOTH FILES NOW$(N) (password manager / encrypted disk). Every future update of the app on\n"; \
	printf "your phone must be signed with this key. Without it the only way to update is uninstall + reinstall,\n"; \
	printf "which deletes the notes stored inside the app. See: make keystore-info\n\n"

keystore-info: env-check ## Show where the release key is and its certificate fingerprint
	@[ -f "$(KEYSTORE_PROPS)" ] || { printf "No release key yet ($(KEYSTORE_PROPS)). Create it with: make keystore\n"; exit 1; }
	@printf "Key files (back up both):\n  $(KEYSTORE_FILE)\n  $(KEYSTORE_PROPS)\n"
	@MDW_KS_PW=$$(sed -n 's/^storePassword=//p' "$(KEYSTORE_PROPS)") \
	  "$$JAVA_HOME/bin/keytool" -list -v -keystore "$(KEYSTORE_FILE)" -storepass:env MDW_KS_PW \
	  | grep -E 'Alias name|Valid from|SHA256:'

devices: ## List connected devices with their Android version
	@[ -x "$(ADB_BIN)" ] || { printf "$(R)ERROR:$(N) adb not found at $(ADB_BIN)\n"; exit 1; }
	@"$(ADB_BIN)" devices -l | sed '/^$$/d'
	@for s in $$("$(ADB_BIN)" devices | awk 'NR>1 && $$2=="device" {print $$1}'); do \
	  printf "  %s: Android %s (API %s)\n" "$$s" \
	    "$$("$(ADB_BIN)" -s $$s shell getprop ro.build.version.release | tr -d '\r')" \
	    "$$("$(ADB_BIN)" -s $$s shell getprop ro.build.version.sdk | tr -d '\r')"; done

pair: ## Pair over Wi-Fi once: make pair HOST=192.168.1.23:37123 CODE=123456 ("Pair device with pairing code")
	@[ -n "$(HOST)" ] && [ -n "$(CODE)" ] || { \
	  printf "Usage: make pair HOST=<ip:port> CODE=<6-digit code>\n"; \
	  printf "Phone: Settings > System > Developer options > Wireless debugging > Pair device with pairing code\n"; \
	  printf "(use the IP address & port shown in THAT dialog; phone and Mac on the same Wi-Fi)\n"; exit 1; }
	@"$(ADB_BIN)" pair "$(HOST)" "$(CODE)"
	@printf "Paired. The phone usually connects by itself within seconds (check: make devices).\n"
	@printf "If not: make connect HOST=<ip:port shown on the Wireless debugging screen - a DIFFERENT port>\n"

connect: ## Connect over Wi-Fi: make connect HOST=192.168.1.23:41234 (without HOST: list discovered phones)
	@if [ -z "$(HOST)" ]; then "$(ADB_BIN)" mdns services; "$(ADB_BIN)" devices -l; \
	else "$(ADB_BIN)" connect "$(HOST)" && "$(ADB_BIN)" devices -l; fi

emulator: ## Boot the Android emulator (AVD=name, default Pixel_10_Pro_XL) and wait until ready
	@[ -x "$(EMULATOR_BIN)" ] || { printf "$(R)ERROR:$(N) emulator not found at $(EMULATOR_BIN)\n"; exit 1; }
	@"$(EMULATOR_BIN)" -list-avds | grep -qx "$(AVD)" || { printf "$(R)ERROR:$(N) no AVD named $(AVD). Available:\n"; "$(EMULATOR_BIN)" -list-avds; exit 1; }
	@if "$(ADB_BIN)" devices | grep -q '^emulator-'; then \
	  printf "An emulator is already running:\n"; "$(ADB_BIN)" devices -l | grep '^emulator-'; exit 0; fi; \
	nohup "$(EMULATOR_BIN)" -avd "$(AVD)" -no-boot-anim $(EMU_FLAGS) >/dev/null 2>&1 & \
	printf "Booting $(AVD)"; for i in $$(seq 1 180); do \
	  s=$$("$(ADB_BIN)" -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r'); \
	  [ "$$s" = "1" ] && { printf " ready.\n"; exit 0; }; printf "."; sleep 2; \
	done; printf "\n$(R)ERROR:$(N) the emulator did not finish booting within 6 minutes\n"; exit 1

logcat: ## Stream the app's logs (release + debug; survives app restarts). Ctrl-C to stop
	@$(PICK_DEVICE); \
	uids=$$("$$adb" -s "$$serial" shell cmd package list packages -U 2>/dev/null | tr -d '\r' | \
	  awk -v a="package:$(APP_ID)" -v b="package:$(APP_ID_DEBUG)" '$$1==a || $$1==b {sub("uid:","",$$2); print $$2}' | paste -sd, -); \
	[ -n "$$uids" ] || { printf "$(R)ERROR:$(N) $(APP_ID) is not installed on $$serial (make install)\n"; exit 1; }; \
	printf "Logs of $(APP_ID)/$(APP_ID_DEBUG) (uid $$uids) on $$serial - Ctrl-C to stop\n"; \
	exec "$$adb" -s "$$serial" logcat -v color --uid="$$uids"

uninstall: ## Uninstall the release app. DELETES ALL NOTES STORED INSIDE THE APP. Needs CONFIRM=yes
	@if [ "$(CONFIRM)" != "yes" ]; then \
	  printf "$(R)Refusing.$(N) Uninstalling $(APP_ID) permanently deletes every note stored inside the app\n"; \
	  printf "(notes in a linked folder you picked yourself are kept). Export your notes first, then run:\n"; \
	  printf "  make uninstall CONFIRM=yes\n"; exit 1; fi
	@$(PICK_DEVICE); "$$adb" -s "$$serial" uninstall "$(APP_ID)"

uninstall-debug: ## Uninstall the debug app (deletes the debug app's notes only)
	@$(PICK_DEVICE); "$$adb" -s "$$serial" uninstall "$(APP_ID_DEBUG)"

backup-notes: ## Copy the DEBUG app's notes to ./notes-backup-<time>/ (release data is not readable over adb)
	@$(PICK_DEVICE); \
	if ! "$$adb" -s "$$serial" shell pm path "$(APP_ID_DEBUG)" >/dev/null 2>&1; then \
	  printf "The debug app ($(APP_ID_DEBUG)) is not installed.\n"; \
	  printf "The RELEASE app's private files cannot be read over adb (run-as needs a debuggable app, adb backup\n"; \
	  printf "skips apps targeting Android 12+). Back up release notes from inside the app, or keep them in a\n"; \
	  printf "linked folder (e.g. Documents/mdwriter) and copy it: adb pull /sdcard/Documents/mdwriter\n"; exit 1; fi; \
	dest="notes-backup-$$(date +%Y%m%d-%H%M%S)"; mkdir -p "$$dest"; \
	"$$adb" -s "$$serial" exec-out run-as "$(APP_ID_DEBUG)" tar -cf - files | tar -xf - -C "$$dest" \
	  && printf "$(G)Saved$(N) debug-app notes to $$dest/ ($$(find "$$dest" -type f | wc -l | tr -d ' ') files)\n"

doctor: ## Check JDK, SDK, signing key and connected devices; explains what is missing
	@printf "$(B)Host$(N)          %s, GNU make %s\n" "$$(uname -sm)" "$(MAKE_VERSION)"
	@printf "$(B)JAVA_HOME$(N)     %s\n" "$(if $(JDK),$(JDK),MISSING - install Android Studio or a JDK $(JDK_MIN)-$(JDK_MAX))"
	@[ -z "$(JDK)" ] || printf "$(B)java$(N)          %s\n" "$$("$$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
	@[ -z "$(ENV_JAVA_HOME)" ] || [ "$(ENV_JAVA_HOME)" = "$(JDK)" ] || printf "$(Y)note$(N)          your JAVA_HOME ($(ENV_JAVA_HOME)) was skipped: not a JDK $(JDK_MIN)-$(JDK_MAX)\n"
	@printf "$(B)SDK$(N)           %s %s\n" "$(SDK)" "$$([ -d "$(SDK)" ] && echo '[ok]' || echo '[MISSING - install Android Studio, or set ANDROID_HOME]')"
	@printf "$(B)platform 37$(N)   %s\n" "$$(ls -d "$(SDK)"/platforms/android-37* 2>/dev/null | xargs -n1 basename 2>/dev/null | tr '\n' ' ' | grep . || echo 'MISSING - Studio > SDK Manager > Android 17 (API 37) SDK Platform')"
	@printf "$(B)build-tools$(N)   %s\n" "$$([ -d "$(BUILD_TOOLS)" ] && echo '36.0.0 [ok]' || echo '36.0.0 missing (Gradle downloads it if licenses are accepted)')"
	@printf "$(B)adb$(N)           %s\n" "$$([ -x "$(ADB_BIN)" ] && "$(ADB_BIN)" version | sed -n 2p || echo 'MISSING - Studio > SDK Manager > SDK Tools > Android SDK Platform-Tools')"
	@printf "$(B)emulator$(N)      %s\n" "$$([ -x "$(EMULATOR_BIN)" ] && echo "AVDs: $$("$(EMULATOR_BIN)" -list-avds 2>/dev/null | tr '\n' ' ')" || echo 'not installed (optional)')"
	@printf "$(B)gradle$(N)        %s\n" "$$(sed -n 's/^distributionUrl=.*gradle-\(.*\)-bin.zip/\1/p' gradle/wrapper/gradle-wrapper.properties 2>/dev/null)"
	@printf "$(B)applicationId$(N) %s (debug: %s)\n" "$(APP_ID)" "$(APP_ID_DEBUG)"
	@printf "$(B)signing key$(N)   %s\n" "$$([ -f "$(KEYSTORE_PROPS)" ] && echo "$(KEYSTORE_FILE) [ok] - keep a backup" || echo "none yet - 'make install' creates it")"
	@printf "$(B)devices$(N)\n"; \
	[ -x "$(ADB_BIN)" ] || exit 0; \
	"$(ADB_BIN)" devices | awk 'NR>1 && NF>=2 {print $$1" "$$2}' | while read -r s st; do \
	  if [ "$$st" != "device" ]; then printf "  %-22s $(Y)%s$(N)\n" "$$s" "$$st"; continue; fi; \
	  api=$$("$(ADB_BIN)" -s "$$s" shell getprop ro.build.version.sdk </dev/null | tr -d '\r'); \
	  rel=$$("$(ADB_BIN)" -s "$$s" shell getprop ro.build.version.release </dev/null | tr -d '\r'); \
	  model=$$("$(ADB_BIN)" -s "$$s" shell getprop ro.product.model </dev/null | tr -d '\r'); \
	  if [ "$$api" -ge $(MIN_API) ]; then ok="$(G)ok$(N)"; else ok="$(R)TOO OLD - needs Android 16 (API $(MIN_API))$(N)"; fi; \
	  printf "  %-22s %s, Android %s (API %s) $$ok\n" "$$s" "$$model" "$$rel" "$$api"; \
	done; \
	"$(ADB_BIN)" devices | awk 'NR>1 && NF>=2' | grep -q . || printf "  (none) - see README 'Install on your phone'\n"

# ------------------------------------------------------------------ internal

env-check: local.properties
	@[ -d "$(SDK)" ] || { printf "$(R)ERROR:$(N) Android SDK not found at $(SDK).\nInstall Android Studio (it installs the SDK) or export ANDROID_HOME=/path/to/sdk\n"; exit 1; }
	@[ -n "$(JDK)" ] || { printf "$(R)ERROR:$(N) no JDK $(JDK_MIN)-$(JDK_MAX) found. Install Android Studio (bundles one) or 'brew install openjdk@21',\nor export JAVA_HOME=/path/to/jdk (Gradle 9 cannot run on JDK 27+).\n"; exit 1; }
	@[ -x "$(GRADLEW)" ] || { printf "$(R)ERROR:$(N) $(GRADLEW) missing or not executable (chmod +x gradlew)\n"; exit 1; }

local.properties:
	@printf 'sdk.dir=%s\n' "$(SDK)" > $@
	@printf "Created local.properties (sdk.dir=$(SDK))\n"
