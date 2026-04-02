                         / Dgp /

                 Deterministically Generated Passwords


    ~ What is Dgp?

      An Android password manager that derives passwords deterministically
      from a seed + account + service name using PBKDF2-HMAC-SHA1 (42,000
      iterations). The same inputs always produce the same password — no
      password database is stored or synced.


    ~ How does it work?

      key = PBKDF2(seed + account, service_name, iterations=42000)

      Output formats: hex, base58, alnum, xkcd wordlist, and long variants.
      The seed is encrypted at rest using AES-256-GCM via the Android
      Keystore, protected by biometric authentication.


    ~ How do I build it?

      Requirements: JDK 21, Android SDK 34

      ./gradlew assembleDebug     # debug APK
      ./gradlew assembleRelease   # release APK


    ~ Is it tested?

      Yes. 50+ test vectors are built into the app and can be run from
      the settings screen. They validate all output formats and edge cases
      (including 64-byte seed pre-hashing).
