/*
 * Copyright (c) 2025, IBM Corporation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package cacerts;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import sun.security.util.UntrustedCertificates;

public class Certificates {

    public static void main(String[] args) throws Exception {
        verifyCacertsTrusted();
        verifyBlockedCertsUntrusted();
    }

    private static void verifyBlockedCertsUntrusted() throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        int blockedCount = 0;
        boolean failed = false;
        try (InputStream in = Certificates.class.getResourceAsStream("/blocked.certs.pem")            ) {
                Collection<? extends Certificate> certs = cf.generateCertificates(in);
                blockedCount = certs.size();
                System.out.println("Testing " + blockedCount + " blocked certificates...");
                for (Certificate c: certs) {
                    X509Certificate cert = ((X509Certificate)c);
                    if (!UntrustedCertificates.isUntrusted(cert)) {
                        System.err.println(cert.getSubjectX500Principal() + " is trusted");
                        failed = true;
                    }
                }
        }
        if (failed || blockedCount == 0) {
            throw new RuntimeException("Test failed! Some blocked certificates are trusted?");
        }
        System.out.println("Blocked certificates test PASSES.");
    }

    private static void verifyCacertsTrusted() throws Exception {
        int untrustedCount = 0;
        int totalCount = 0;
        TrustManagerFactory factory = TrustManagerFactory.getInstance("PKIX");
        factory.init((KeyStore)null);
        for (TrustManager tm :factory.getTrustManagers()) {
            if (tm instanceof X509TrustManager xtm) {
                for (X509Certificate c: xtm.getAcceptedIssuers()) {
                    if (UntrustedCertificates.isUntrusted(c)) {
                        untrustedCount++;
                    };
                    totalCount++;
                };
            }
        }
        if (untrustedCount > 0 || totalCount == 0) {
            throw new RuntimeException("Test failed. " + untrustedCount + " of " + totalCount + " in cacerts untrusted!");
        }
        System.out.println("Checked " + totalCount + " certificates. PASS!");
    }

}
