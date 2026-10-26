# Project-specific R8 rules go here.

# Kadb makes its host certificate through the Bouncy Castle JCA provider, which
# registers its algorithms as class-name strings (".../RSA$Mappings") and loads
# them reflectively. R8 can't see those, so it renames or strips them. Kadb asks
# that provider for three things only: SHA256withRSA, RSA keys and X.509
# certificates. These keep exactly those; keeping the whole provider cost
# ~700 KB of the APK. Any other lookup falls through to Kadb's next provider
# (AndroidOpenSSL, then the platform default) rather than failing.
-keep class org.bouncycastle.jcajce.provider.asymmetric.RSA$Mappings { <init>(); }
-keep class org.bouncycastle.jcajce.provider.asymmetric.X509$Mappings { <init>(); }
-keep class org.bouncycastle.jcajce.provider.asymmetric.rsa.DigestSignatureSpi$SHA256 { <init>(); }
-keep class org.bouncycastle.jcajce.provider.asymmetric.rsa.KeyFactorySpi { <init>(); }
-keep class org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory { <init>(); }
-dontwarn org.bouncycastle.**

# Kadb itself: pairing reaches Conscrypt and hidden APIs by name. Small enough
# to keep whole rather than chase each reflective call.
-keep class com.flyfishxu.kadb.** { *; }
