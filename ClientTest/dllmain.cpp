#include "pch.h"
#include <jni.h>
#include <wininet.h>

#pragma optimize("", off)
#pragma strict_gs_check(off)

constexpr const char* ENTRY_CLASS = "com.hades.client.HadesAgent";
constexpr const char* ENTRY_METHOD = "initialize";

using fnGetCreatedJavaVMs = jint(JNICALL*)(JavaVM**, jsize, jsize*);

static HMODULE g_hModule = nullptr;
static wchar_t g_logPath[MAX_PATH] = { 0 };
static char g_sessionToken[4096] = { 0 };
static BYTE g_tokenXorKey = 0;
static bool g_tokenEncrypted = false;

static void EnsureLogPathInitialized()
{
    if (g_logPath[0] != L'\0') return;

    wchar_t tempDir[MAX_PATH]{};
    GetTempPathW(MAX_PATH, tempDir);
    wcscpy_s(g_logPath, MAX_PATH, tempDir);
    wcscat_s(g_logPath, MAX_PATH, L"hades_inject.txt");
}

static void Log(const char* msg)
{
    EnsureLogPathInitialized();

    HANDLE hFile = CreateFileW(g_logPath, FILE_APPEND_DATA,
        FILE_SHARE_READ, nullptr, OPEN_ALWAYS,
        FILE_ATTRIBUTE_NORMAL, nullptr);
    if (hFile == INVALID_HANDLE_VALUE) return;

    char line[2048];
    strcpy_s(line, "[Hades] ");
    strcat_s(line, msg);
    strcat_s(line, "\r\n");

    DWORD written = 0;
    WriteFile(hFile, line, static_cast<DWORD>(strlen(line)), &written, nullptr);
    CloseHandle(hFile);
}

static void SetSessionToken(const char* token)
{
    SecureZeroMemory(&g_tokenXorKey, sizeof(g_tokenXorKey));
    g_tokenXorKey = static_cast<BYTE>((GetTickCount64() & 0xFF) ^ 0xA7);
    if (g_tokenXorKey == 0) g_tokenXorKey = 0xA7;

    if (token) {
        strncpy_s(g_sessionToken, token, _TRUNCATE);
    } else {
        g_sessionToken[0] = '\0';
    }
    
    size_t len = strlen(g_sessionToken);
    for (size_t i = 0; i < len; i++) {
        g_sessionToken[i] = static_cast<char>(static_cast<BYTE>(g_sessionToken[i]) ^ g_tokenXorKey);
    }

    g_tokenEncrypted = true;
}
static void GetSessionTokenPlain(char* outBuffer, size_t outSize)
{
    if (!g_tokenEncrypted) {
        if (outSize > 0) outBuffer[0] = '\0';
        return;
    }
    size_t len = strlen(g_sessionToken);
    if (len >= outSize) len = outSize - 1;
    for (size_t i = 0; i < len; i++) {
        outBuffer[i] = static_cast<char>(static_cast<BYTE>(g_sessionToken[i]) ^ g_tokenXorKey);
    }
    outBuffer[len] = '\0';
}

static void SecureClearToken()
{
    SecureZeroMemory(g_sessionToken, sizeof(g_sessionToken));
    SecureZeroMemory(&g_tokenXorKey, sizeof(g_tokenXorKey));
    g_tokenEncrypted = false;
}

static void WideToUtf8(const wchar_t* wide, char* outBuffer, int outSize)
{
    if (!wide || outSize <= 0) return;
    outBuffer[0] = '\0';
    WideCharToMultiByte(CP_UTF8, 0, wide, -1, outBuffer, outSize, nullptr, nullptr);
}

static jobject GetParentClassLoader(JNIEnv* env)
{
    jclass threadClass = env->FindClass("java/lang/Thread");
    jmethodID getContextCL = env->GetMethodID(threadClass, "getContextClassLoader", "()Ljava/lang/ClassLoader;");
    jmethodID getThreadName = env->GetMethodID(threadClass, "getName", "()Ljava/lang/String;");
    jmethodID getAllTraces = env->GetStaticMethodID(threadClass, "getAllStackTraces", "()Ljava/util/Map;");

    jobject traceMap = env->CallStaticObjectMethod(threadClass, getAllTraces);
    jclass mapClass = env->FindClass("java/util/Map");
    jmethodID keySetMethod = env->GetMethodID(mapClass, "keySet", "()Ljava/util/Set;");
    jobject keySet = env->CallObjectMethod(traceMap, keySetMethod);

    jclass setClass = env->FindClass("java/util/Set");
    jmethodID toArray = env->GetMethodID(setClass, "toArray", "()[Ljava/lang/Object;");
    jobjectArray threads = static_cast<jobjectArray>(env->CallObjectMethod(keySet, toArray));

    jobject parentCL = nullptr;
    jsize threadCount = env->GetArrayLength(threads);

    for (jsize i = 0; i < threadCount; i++)
    {
        jobject t = env->GetObjectArrayElement(threads, i);
        jstring name = static_cast<jstring>(env->CallObjectMethod(t, getThreadName));
        const char* nameChars = env->GetStringUTFChars(name, nullptr);
        bool isClientThread = (strcmp(nameChars, "Client thread") == 0);
        env->ReleaseStringUTFChars(name, nameChars);

        jobject cl = env->CallObjectMethod(t, getContextCL);
        if (cl)
        {
            if (isClientThread)
            {
                parentCL = cl;
                env->DeleteLocalRef(t);
                break;
            }
            if (!parentCL)
                parentCL = cl;
        }
        env->DeleteLocalRef(t);
    }

    if (!parentCL)
    {
        jclass clClass = env->FindClass("java/lang/ClassLoader");
        jmethodID getSysCL = env->GetStaticMethodID(clClass, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
        parentCL = env->CallStaticObjectMethod(clClass, getSysCL);
    }

    return parentCL;
}

static DWORD WINAPI MainThread(LPVOID)
{
    wchar_t tempDir[MAX_PATH]{};
    GetTempPathW(MAX_PATH, tempDir);
    EnsureLogPathInitialized();
    DeleteFileW(g_logPath);

    Log("MainThread started");

    wchar_t jarPathW[MAX_PATH];
    wcscpy_s(jarPathW, tempDir);
    wcscat_s(jarPathW, L"client.jar");

    Log("Validating Auth Token and fetching secure JAR path...");
    char tokenPlain[4096];
    GetSessionTokenPlain(tokenPlain, sizeof(tokenPlain));

    // Dynamically load WinINet to avoid static PE imports crashing manual mappers
    HMODULE hWinINet = LoadLibraryA("wininet.dll");
    if (!hWinINet) {
        Log("Failed to LoadLibrary wininet.dll");
        return 1;
    }

    typedef HINTERNET(WINAPI* fnInternetOpenW)(LPCWSTR, DWORD, LPCWSTR, LPCWSTR, DWORD);
    typedef HINTERNET(WINAPI* fnInternetConnectW)(HINTERNET, LPCWSTR, INTERNET_PORT, LPCWSTR, LPCWSTR, DWORD, DWORD, DWORD_PTR);
    typedef HINTERNET(WINAPI* fnHttpOpenRequestW)(HINTERNET, LPCWSTR, LPCWSTR, LPCWSTR, LPCWSTR, LPCWSTR*, DWORD, DWORD_PTR);
    typedef BOOL(WINAPI* fnHttpSendRequestA)(HINTERNET, LPCSTR, DWORD, LPVOID, DWORD);
    typedef BOOL(WINAPI* fnInternetReadFile)(HINTERNET, LPVOID, DWORD, LPDWORD);
    typedef BOOL(WINAPI* fnInternetCloseHandle)(HINTERNET);

    auto pInternetOpenW = (fnInternetOpenW)GetProcAddress(hWinINet, "InternetOpenW");
    auto pInternetConnectW = (fnInternetConnectW)GetProcAddress(hWinINet, "InternetConnectW");
    auto pHttpOpenRequestW = (fnHttpOpenRequestW)GetProcAddress(hWinINet, "HttpOpenRequestW");
    auto pHttpSendRequestA = (fnHttpSendRequestA)GetProcAddress(hWinINet, "HttpSendRequestA");
    auto pInternetReadFile = (fnInternetReadFile)GetProcAddress(hWinINet, "InternetReadFile");
    auto pInternetCloseHandle = (fnInternetCloseHandle)GetProcAddress(hWinINet, "InternetCloseHandle");

    typedef BOOL(WINAPI* fnInternetCrackUrlW)(LPCWSTR, DWORD, DWORD, LPURL_COMPONENTSW);
    auto pInternetCrackUrlW = (fnInternetCrackUrlW)GetProcAddress(hWinINet, "InternetCrackUrlW");

    HINTERNET hSession = pInternetOpenW(L"HadesInject/1.0", INTERNET_OPEN_TYPE_PRECONFIG, nullptr, nullptr, 0);
    if (!hSession) {
        Log("InternetOpenW failed");
        return 1;
    }

    HINTERNET hConnect = pInternetConnectW(hSession, L"szxxwxwityixqzzmarlq.supabase.co", INTERNET_DEFAULT_HTTPS_PORT, nullptr, nullptr, INTERNET_SERVICE_HTTP, 0, 0);
    if (!hConnect) {
        Log("InternetConnectW failed");
        pInternetCloseHandle(hSession);
        return 1;
    }

    HINTERNET hRequest = pHttpOpenRequestW(hConnect, L"GET", L"/functions/v1/launcher-jar-download", nullptr, nullptr, nullptr, INTERNET_FLAG_SECURE | INTERNET_FLAG_RELOAD, 0);
    if (!hRequest) {
        Log("HttpOpenRequestW failed");
        pInternetCloseHandle(hConnect);
        pInternetCloseHandle(hSession);
        return 1;
    }

    char headers[4096];
    strcpy_s(headers, "Authorization: Bearer ");
    strcat_s(headers, tokenPlain);
    strcat_s(headers, "\r\n");

    if (!pHttpSendRequestA(hRequest, headers, static_cast<DWORD>(strlen(headers)), nullptr, 0)) {
        Log("HttpSendRequestA failed [Token Authentication Failed]");
        pInternetCloseHandle(hRequest);
        pInternetCloseHandle(hConnect);
        pInternetCloseHandle(hSession);
        return 1;
    }

    // Allocate 128KB buffer for the JSON Response
    char* response = (char*)HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, 128 * 1024);
    if (!response) {
        Log("HeapAlloc failed");
        return 1;
    }

    char buffer[4096];
    DWORD bytesRead = 0;
    while (pInternetReadFile(hRequest, buffer, sizeof(buffer) - 1, &bytesRead) && bytesRead > 0) {
        buffer[bytesRead] = '\0';
        strcat_s(response, 128 * 1024, buffer);
    }

    pInternetCloseHandle(hRequest);
    pInternetCloseHandle(hConnect);
    pInternetCloseHandle(hSession);

    // Log the raw response for debugging (truncate to 200 chars)
    {
        char logSnippet[256] = "Backend response: ";
        size_t respLen = strlen(response);
        if (respLen > 200) respLen = 200;
        strncat_s(logSnippet, response, respLen);
        Log(logSnippet);
    }

    char* urlPos = strstr(response, "\"url\"");
    if (!urlPos) {
        Log("Invalid Backend Response (No URL found)");
        HeapFree(GetProcessHeap(), 0, response);
        return 1;
    }

    char* httpStart = strstr(urlPos, "http");
    if (!httpStart) {
        Log("Failed to locate http protocol in JSON response");
        HeapFree(GetProcessHeap(), 0, response);
        return 1;
    }

    char* endQuote = strchr(httpStart, '\"');
    if (!endQuote) {
        Log("JSON string parsing error bounding signed URL");
        HeapFree(GetProcessHeap(), 0, response);
        return 1;
    }

    *endQuote = '\0'; // Null terminate directly inside the JSON buffer

    wchar_t wSignedUrl[4096];
    MultiByteToWideChar(CP_UTF8, 0, httpStart, -1, wSignedUrl, 4096);

    HeapFree(GetProcessHeap(), 0, response);

    Log("Secure Signed URL obtained. Downloading Binary...");

    // Parse the signed URL into host + path using InternetCrackUrlW
    URL_COMPONENTSW urlComponents = {};
    urlComponents.dwStructSize = sizeof(urlComponents);
    wchar_t hostName[256] = {};
    wchar_t urlPath[4096] = {};
    urlComponents.lpszHostName = hostName;
    urlComponents.dwHostNameLength = 256;
    urlComponents.lpszUrlPath = urlPath;
    urlComponents.dwUrlPathLength = 4096;

    if (!pInternetCrackUrlW || !pInternetCrackUrlW(wSignedUrl, 0, 0, &urlComponents)) {
        Log("InternetCrackUrlW failed - cannot parse signed URL");
        if (GetFileAttributesW(jarPathW) == INVALID_FILE_ATTRIBUTES) {
            return 1;
        }
    }
    else {
        // Append the ExtraInfo (query string with ?token=...) to urlPath
        if (urlComponents.lpszExtraInfo && urlComponents.dwExtraInfoLength > 0) {
            wcscat_s(urlPath, urlComponents.lpszExtraInfo);
        }

        HINTERNET hDlSession = pInternetOpenW(L"HadesInject/1.0", INTERNET_OPEN_TYPE_PRECONFIG, nullptr, nullptr, 0);
        HINTERNET hDlConnect = hDlSession ? pInternetConnectW(hDlSession, hostName,
            urlComponents.nPort ? urlComponents.nPort : INTERNET_DEFAULT_HTTPS_PORT,
            nullptr, nullptr, INTERNET_SERVICE_HTTP, 0, 0) : nullptr;
        HINTERNET hDlRequest = hDlConnect ? pHttpOpenRequestW(hDlConnect, L"GET", urlPath, nullptr, nullptr, nullptr,
            INTERNET_FLAG_SECURE | INTERNET_FLAG_RELOAD | INTERNET_FLAG_NO_CACHE_WRITE, 0) : nullptr;

        bool downloaded = false;
        if (hDlRequest && pHttpSendRequestA(hDlRequest, nullptr, 0, nullptr, 0)) {
            HANDLE hFile = CreateFileW(jarPathW, GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
            if (hFile != INVALID_HANDLE_VALUE) {
                char dlBuf[8192];
                DWORD dlRead = 0;
                while (pInternetReadFile(hDlRequest, dlBuf, sizeof(dlBuf), &dlRead) && dlRead > 0) {
                    DWORD written = 0;
                    WriteFile(hFile, dlBuf, dlRead, &written, nullptr);
                }
                CloseHandle(hFile);
                downloaded = true;
                Log("JAR downloaded successfully via WinINet");
            } else {
                Log("CreateFileW for JAR failed");
            }
        } else {
            Log("HttpSendRequestA for JAR download failed");
        }

        if (hDlRequest) pInternetCloseHandle(hDlRequest);
        if (hDlConnect) pInternetCloseHandle(hDlConnect);
        if (hDlSession) pInternetCloseHandle(hDlSession);

        if (!downloaded && GetFileAttributesW(jarPathW) == INVALID_FILE_ATTRIBUTES) {
            Log("No existing JAR found either");
            return 1;
        }
    }

    HMODULE hJvm = GetModuleHandleA("jvm.dll");
    if (!hJvm)
    {
        Log("jvm.dll not found");
        return 1;
    }

    auto pGetCreatedJavaVMs = reinterpret_cast<fnGetCreatedJavaVMs>(GetProcAddress(hJvm, "JNI_GetCreatedJavaVMs"));
    if (!pGetCreatedJavaVMs)
    {
        Log("JNI_GetCreatedJavaVMs missing");
        return 1;
    }

    JavaVM* jvm = nullptr;
    jsize vmCount = 0;
    if (pGetCreatedJavaVMs(&jvm, 1, &vmCount) != JNI_OK || vmCount == 0)
    {
        Log("No active JVM");
        return 1;
    }

    JNIEnv* env = nullptr;
    bool needsDetach = false;
    jint getEnvResult = jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8);
    if (getEnvResult == JNI_EDETACHED)
    {
        if (jvm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) != JNI_OK)
        {
            Log("AttachCurrentThread failed");
            return 1;
        }
        needsDetach = true;
    }
    else if (getEnvResult != JNI_OK)
    {
        Log("GetEnv failed");
        return 1;
    }

    [&]()
    {
        char jarUtf8[512];
        WideToUtf8(jarPathW, jarUtf8, sizeof(jarUtf8));
        for (int i = 0; jarUtf8[i] != '\0'; i++) if (jarUtf8[i] == '\\') jarUtf8[i] = '/';
        
        char jarUrl[512];
        strcpy_s(jarUrl, "file:///");
        strcat_s(jarUrl, jarUtf8);

        jclass urlClass = env->FindClass("java/net/URL");
        jmethodID urlCtor = env->GetMethodID(urlClass, "<init>", "(Ljava/lang/String;)V");
        jstring jUrlStr = env->NewStringUTF(jarUrl);
        jobject urlObj = env->NewObject(urlClass, urlCtor, jUrlStr);
        if (!urlObj || env->ExceptionCheck())
        {
            Log("URL object creation failed");
            env->ExceptionDescribe();
            env->ExceptionClear();
            return;
        }

        // Try to find the LaunchClassLoader from the Client thread
        // and add our JAR directly to it. This is CRITICAL because ByteBuddy
        // inlines our hook code into MC classes, and the inlined references
        // must be resolvable by the same classloader that loads MC classes.
        jobject launchCL = nullptr;
        {
            jclass threadClass = env->FindClass("java/lang/Thread");
            if (!threadClass || env->ExceptionCheck()) { 
                if (env->ExceptionCheck()) env->ExceptionClear();
                Log("FindClass Thread failed"); 
                goto fallback; 
            }
            jmethodID getContextCL = env->GetMethodID(threadClass, "getContextClassLoader", "()Ljava/lang/ClassLoader;");
            jmethodID getThreadName = env->GetMethodID(threadClass, "getName", "()Ljava/lang/String;");
            jmethodID getAllTraces = env->GetStaticMethodID(threadClass, "getAllStackTraces", "()Ljava/util/Map;");
            if (!getContextCL || !getThreadName || !getAllTraces) {
                Log("Thread method lookups failed");
                if (env->ExceptionCheck()) env->ExceptionClear();
                goto fallback;
            }

            jobject traceMap = env->CallStaticObjectMethod(threadClass, getAllTraces);
            if (!traceMap) { Log("getAllStackTraces returned null"); goto fallback; }

            jclass mapClass = env->FindClass("java/util/Map");
            if (!mapClass) { Log("FindClass Map failed"); goto fallback; }
            jmethodID keySetMethod = env->GetMethodID(mapClass, "keySet", "()Ljava/util/Set;");
            if (!keySetMethod) { Log("keySet method not found"); goto fallback; }
            jobject keySet = env->CallObjectMethod(traceMap, keySetMethod);
            if (!keySet) { Log("keySet returned null"); goto fallback; }

            jclass setClass = env->FindClass("java/util/Set");
            if (!setClass) { Log("FindClass Set failed"); goto fallback; }
            jmethodID toArray = env->GetMethodID(setClass, "toArray", "()[Ljava/lang/Object;");
            if (!toArray) { Log("toArray method not found"); goto fallback; }
            jobjectArray threads = static_cast<jobjectArray>(env->CallObjectMethod(keySet, toArray));
            if (!threads) { Log("toArray returned null"); goto fallback; }
            jsize threadCount = env->GetArrayLength(threads);
            Log("Searching threads for LaunchClassLoader...");

            // Also get Class.getName() method once
            jclass classClass = env->FindClass("java/lang/Class");
            jmethodID getNameMethod = classClass ? env->GetMethodID(classClass, "getName", "()Ljava/lang/String;") : nullptr;

            for (jsize i = 0; i < threadCount; i++)
            {
                jobject t = env->GetObjectArrayElement(threads, i);
                if (!t) continue;
                jstring name = static_cast<jstring>(env->CallObjectMethod(t, getThreadName));
                if (!name) { env->DeleteLocalRef(t); continue; }
                const char* nameChars = env->GetStringUTFChars(name, nullptr);
                if (!nameChars) { env->DeleteLocalRef(name); env->DeleteLocalRef(t); continue; }
                bool isClientThread = (strcmp(nameChars, "Client thread") == 0);
                env->ReleaseStringUTFChars(name, nameChars);
                env->DeleteLocalRef(name);

                if (isClientThread)
                {
                    jobject cl = env->CallObjectMethod(t, getContextCL);
                    if (cl && getNameMethod)
                    {
                        jclass clazz = env->GetObjectClass(cl);
                        if (clazz)
                        {
                            jstring clName = static_cast<jstring>(env->CallObjectMethod(clazz, getNameMethod));
                            if (clName)
                            {
                                const char* clNameChars = env->GetStringUTFChars(clName, nullptr);
                                if (clNameChars)
                                {
                                    Log("Client thread CL: ");
                                    Log(clNameChars);
                                    if (strstr(clNameChars, "LaunchClassLoader"))
                                    {
                                        launchCL = cl;
                                    }
                                    env->ReleaseStringUTFChars(clName, clNameChars);
                                }
                                env->DeleteLocalRef(clName);
                            }
                            env->DeleteLocalRef(clazz);
                        }
                    }
                    env->DeleteLocalRef(t);
                    break;
                }
                env->DeleteLocalRef(t);
            }
        }
        fallback:

        jobject loader = nullptr;
        if (launchCL)
        {
            // Add our JAR URL directly to the LaunchClassLoader.
            // LaunchClassLoader overrides addURL() as PUBLIC, so we look it up
            // on its own class (not URLClassLoader). JNI bypasses Java access checks.
            Log("Found LaunchClassLoader - injecting JAR URL into it");
            jclass lcClass = env->GetObjectClass(launchCL);
            if (!lcClass) {
                Log("GetObjectClass(launchCL) returned null");
                return;
            }
            
            jmethodID addURLMethod = env->GetMethodID(lcClass, "addURL", "(Ljava/net/URL;)V");
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                // LaunchClassLoader might not have addURL, try parent class
                Log("addURL not found on LaunchClassLoader, trying URLClassLoader");
                jclass urlCLClass = env->FindClass("java/net/URLClassLoader");
                addURLMethod = env->GetMethodID(urlCLClass, "addURL", "(Ljava/net/URL;)V");
                if (env->ExceptionCheck()) {
                    env->ExceptionDescribe();
                    env->ExceptionClear();
                    addURLMethod = nullptr;
                }
            }
            
            if (addURLMethod)
            {
                env->CallVoidMethod(launchCL, addURLMethod, urlObj);
                if (env->ExceptionCheck())
                {
                    Log("addURL call threw exception");
                    env->ExceptionDescribe();
                    env->ExceptionClear();
                }
                else
                {
                    Log("Successfully injected JAR into LaunchClassLoader");
                }
            }
            else
            {
                Log("Could not find addURL method on any classloader class");
            }
            
            loader = launchCL;

            // Also add transformer exclusions for our packages
            jmethodID addExclusion = env->GetMethodID(lcClass, "addTransformerExclusion", "(Ljava/lang/String;)V");
            if (addExclusion && !env->ExceptionCheck())
            {
                env->CallVoidMethod(launchCL, addExclusion, env->NewStringUTF("com.hades."));
                env->CallVoidMethod(launchCL, addExclusion, env->NewStringUTF("net.bytebuddy."));
                Log("Added transformer exclusions to LaunchClassLoader");
            }
            else
            {
                if (env->ExceptionCheck()) env->ExceptionClear();
            }

            // Set the LaunchClassLoader as the current thread's context CL too
            jclass threadClass2 = env->FindClass("java/lang/Thread");
            jmethodID currentThread = env->GetStaticMethodID(threadClass2, "currentThread", "()Ljava/lang/Thread;");
            jobject curThread = env->CallStaticObjectMethod(threadClass2, currentThread);
            jmethodID setContextCL = env->GetMethodID(threadClass2, "setContextClassLoader", "(Ljava/lang/ClassLoader;)V");
            env->CallVoidMethod(curThread, setContextCL, launchCL);
        }
        else
        {
            // Fallback: create a standalone URLClassLoader (old behavior)
            Log("LaunchClassLoader not found, creating standalone URLClassLoader");
            jobjectArray urlArray = env->NewObjectArray(1, urlClass, urlObj);
            jobject parentCL = GetParentClassLoader(env);

            jclass urlCLClass = env->FindClass("java/net/URLClassLoader");
            jmethodID urlCLCtor = env->GetMethodID(urlCLClass, "<init>", "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
            loader = env->NewObject(urlCLClass, urlCLCtor, urlArray, parentCL);
            if (!loader || env->ExceptionCheck())
            {
                Log("URLClassLoader creation failed");
                env->ExceptionDescribe();
                env->ExceptionClear();
                return;
            }

            jobject gLoaderRef = env->NewGlobalRef(loader);
            jclass threadClass2 = env->FindClass("java/lang/Thread");
            jmethodID currentThread = env->GetStaticMethodID(threadClass2, "currentThread", "()Ljava/lang/Thread;");
            jobject curThread = env->CallStaticObjectMethod(threadClass2, currentThread);
            jmethodID setContextCL = env->GetMethodID(threadClass2, "setContextClassLoader", "(Ljava/lang/ClassLoader;)V");
            env->CallVoidMethod(curThread, setContextCL, gLoaderRef);
            loader = gLoaderRef;
        }

        jclass urlCLClass2 = env->FindClass("java/net/URLClassLoader");
        jmethodID loadClass = env->GetMethodID(urlCLClass2, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
        jstring jClassName = env->NewStringUTF(ENTRY_CLASS);
        jclass entryClass = static_cast<jclass>(env->CallObjectMethod(loader, loadClass, jClassName));
        if (!entryClass || env->ExceptionCheck())
        {
            Log("Entry class load failed");
            env->ExceptionDescribe();
            env->ExceptionClear();
            return;
        }

        jmethodID entryMethod = env->GetStaticMethodID(entryClass, ENTRY_METHOD, "(Ljava/lang/String;)V");
        if (entryMethod && !env->ExceptionCheck())
        {
            char cToken[4096];
            GetSessionTokenPlain(cToken, sizeof(cToken));
            Log("Calling initialize(String) with Token");
            jstring jToken = env->NewStringUTF(cToken);
            env->CallStaticVoidMethod(entryClass, entryMethod, jToken);
        }
        else
        {
            if (env->ExceptionCheck()) env->ExceptionClear();
            entryMethod = env->GetStaticMethodID(entryClass, ENTRY_METHOD, "()V");
            if (!entryMethod || env->ExceptionCheck())
            {
                Log("initialize method not found");
                env->ExceptionDescribe();
                env->ExceptionClear();
                return;
            }
            env->CallStaticVoidMethod(entryClass, entryMethod);
        }

        if (env->ExceptionCheck())
        {
            Log("Entry method threw exception");
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        else
        {
            Log("Entry method returned OK");
        }
    }();

    if (needsDetach)
        jvm->DetachCurrentThread();

    SecureClearToken();
    Log("Ejecting DLL");
    // FreeLibrary is inherently unsafe in Manual Mapped injection.
    // We gracefully return from the thread instead. The map memory can safely remain in the virtual space.
    return 0;
}

BOOL APIENTRY DllMain(HMODULE hModule, DWORD ul_reason_for_call, LPVOID)
{
    if (ul_reason_for_call == DLL_PROCESS_ATTACH)
    {
        DisableThreadLibraryCalls(hModule);
        g_hModule = hModule;

        // Waiting for the Injector to call StartInjectionWithToken
    }
    return TRUE;
}

extern "C" __declspec(dllexport) DWORD WINAPI StartInjectionWithToken(LPVOID lpToken)
{

    if (lpToken)
    {
        SetSessionToken(static_cast<const char*>(lpToken));
        Log("StartInjectionWithToken: received token");
    }
    else
    {
        SetSessionToken("");
        Log("StartInjectionWithToken: no token pointer provided");
    }

    HANDLE hThread = CreateThread(nullptr, 0, MainThread, nullptr, 0, nullptr);
    if (!hThread) return 1;
    CloseHandle(hThread);
    return 0;
}

