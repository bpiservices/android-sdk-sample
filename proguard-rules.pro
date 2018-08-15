-keep class net.sf.scuba.smartcards.IsoDepCardService {*;}
-keep class org.jmrtd.** { *; }
-keep class net.sf.scuba.** {*;}
-keep class org.spongycastle.** {*;}
-keep class org.ejbca.** {*;}

-dontwarn java.nio.** -dontwarn org.codehaus.**
-dontwarn org.ejbca.** -dontwarn org.spongycastle.**
-dontwarn org.spongycastle.**