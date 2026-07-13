LOCAL_PATH := $(call my-dir)

# ======================================================
# 1) Biblioteca opus (prebuilt)
# ======================================================
include $(CLEAR_VARS)
LOCAL_MODULE := libopus
LOCAL_SRC_FILES := vendor/libs/libopus.a
include $(PREBUILT_STATIC_LIBRARY)

# ======================================================
# 2) Biblioteca SAMPMobileCef (prebuilt)  👈 ADICIONADO
# ======================================================
include $(CLEAR_VARS)
LOCAL_MODULE := libSAMPMobileCef
LOCAL_SRC_FILES := vendor/cef/libSAMPMobileCef.a
include $(PREBUILT_STATIC_LIBRARY)

# ======================================================
# 3) Módulo principal do jogo/lib
# ======================================================
include $(CLEAR_VARS)
LOCAL_MODULE := libSAMP

LOCAL_C_INCLUDES += $(wildcard $(LOCAL_PATH)/vendor/)

# Arquivos fonte
FILE_LIST := $(wildcard $(LOCAL_PATH)/*.c*)
FILE_LIST += $(wildcard $(LOCAL_PATH)/**/*.c*)
FILE_LIST += $(wildcard $(LOCAL_PATH)/**/**/*.c*)
FILE_LIST += $(wildcard $(LOCAL_PATH)/**/**/**/*.c*)
FILE_LIST += $(wildcard $(LOCAL_PATH)/**/**/**/**/*.c*)
FILE_LIST += $(wildcard $(LOCAL_PATH)/**/**/**/**/**/*.c*)
FILE_LIST += $(wildcard $(LOCAL_PATH)/**/**/**/**/**/**/*.c*)

LOCAL_SRC_FILES := $(FILE_LIST:$(LOCAL_PATH)/%=%)

# Bibliotecas padrões
LOCAL_LDLIBS := -llog -lz -ljnigraphics -landroid -lEGL -lGLESv2 -lOpenSLES

# Linkagem das libs estáticas
LOCAL_STATIC_LIBRARIES := \
    libopus \
    libSAMPMobileCef    # 👈 ADICIONADO

# Flags de compilação
LOCAL_CPPFLAGS := -w -s -Wall -fvisibility=default -pthread -O2 -std=c++14 -fexceptions -frtti -fstrict-aliasing -fno-omit-frame-pointer -mfloat-abi=soft -fstack-protector -fno-short-enums
LOCAL_CFLAGS := -DRAKSAMP_CLIENT

# Build final
include $(BUILD_SHARED_LIBRARY)
