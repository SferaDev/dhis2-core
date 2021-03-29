/*
 * Copyright (c) 2004-2021, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.webapi.security.utils;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author Morten Svanæs <msvanaes@dhis2.org>
 */
public final class TestKeys
{
    public static final KeyFactory kf;
    static
    {
        try
        {
            kf = KeyFactory.getInstance( "RSA" );
        }
        catch ( NoSuchAlgorithmException ex )
        {
            throw new IllegalStateException( ex );
        }
    }

    public static final String DEFAULT_ENCODED_SECRET_KEY = "bCzY/M48bbkwBEWjmNSIEPfwApcvXOnkCxORBEbPr+4=";

    public static final SecretKey DEFAULT_SECRET_KEY = new SecretKeySpec(
        Base64.getDecoder().decode( DEFAULT_ENCODED_SECRET_KEY ), "AES" );

    public static final String DEFAULT_RSA_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3FlqJr5TRskIQIgdE3Dd"
        + "7D9lboWdcTUT8a+fJR7MAvQm7XXNoYkm3v7MQL1NYtDvL2l8CAnc0WdSTINU6IRv"
        + "c5Kqo2Q4csNX9SHOmEfzoROjQqahEcve1jBXluoCXdYuYpx4/1tfRgG6ii4Uhxh6"
        + "iI8qNMJQX+fLfqhbfYfxBQVRPywBkAbIP4x1EAsbC6FSNmkhCxiMNqEgxaIpY8C2"
        + "kJdJ/ZIV+WW4noDdzpKqHcwmB8FsrumlVY/DNVvUSDIipiq9PbP4H99TXN1o746o"
        + "RaNa07rq1hoCgMSSy+85SagCoxlmyE+D+of9SsMY8Ol9t0rdzpobBuhyJ/o5dfvj"
        + "KwIDAQAB";

    public static final RSAPublicKey DEFAULT_PUBLIC_KEY;
    static
    {
        X509EncodedKeySpec spec = new X509EncodedKeySpec( Base64.getDecoder().decode( DEFAULT_RSA_PUBLIC_KEY ) );
        try
        {
            DEFAULT_PUBLIC_KEY = (RSAPublicKey) kf.generatePublic( spec );
        }
        catch ( InvalidKeySpecException ex )
        {
            throw new IllegalArgumentException( ex );
        }
    }

    public static final String DEFAULT_RSA_PRIVATE_KEY = "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDcWWomvlNGyQhA"
        + "iB0TcN3sP2VuhZ1xNRPxr58lHswC9Cbtdc2hiSbe/sxAvU1i0O8vaXwICdzRZ1JM"
        + "g1TohG9zkqqjZDhyw1f1Ic6YR/OhE6NCpqERy97WMFeW6gJd1i5inHj/W19GAbqK"
        + "LhSHGHqIjyo0wlBf58t+qFt9h/EFBVE/LAGQBsg/jHUQCxsLoVI2aSELGIw2oSDF"
        + "oiljwLaQl0n9khX5ZbiegN3OkqodzCYHwWyu6aVVj8M1W9RIMiKmKr09s/gf31Nc"
        + "3WjvjqhFo1rTuurWGgKAxJLL7zlJqAKjGWbIT4P6h/1Kwxjw6X23St3OmhsG6HIn"
        + "+jl1++MrAgMBAAECggEBAMf820wop3pyUOwI3aLcaH7YFx5VZMzvqJdNlvpg1jbE"
        + "E2Sn66b1zPLNfOIxLcBG8x8r9Ody1Bi2Vsqc0/5o3KKfdgHvnxAB3Z3dPh2WCDek"
        + "lCOVClEVoLzziTuuTdGO5/CWJXdWHcVzIjPxmK34eJXioiLaTYqN3XKqKMdpD0ZG"
        + "mtNTGvGf+9fQ4i94t0WqIxpMpGt7NM4RHy3+Onggev0zLiDANC23mWrTsUgect/7"
        + "62TYg8g1bKwLAb9wCBT+BiOuCc2wrArRLOJgUkj/F4/gtrR9ima34SvWUyoUaKA0"
        + "bi4YBX9l8oJwFGHbU9uFGEMnH0T/V0KtIB7qetReywkCgYEA9cFyfBIQrYISV/OA"
        + "+Z0bo3vh2aL0QgKrSXZ924cLt7itQAHNZ2ya+e3JRlTczi5mnWfjPWZ6eJB/8MlH"
        + "Gpn12o/POEkU+XjZZSPe1RWGt5g0S3lWqyx9toCS9ACXcN9tGbaqcFSVI73zVTRA"
        + "8J9grR0fbGn7jaTlTX2tnlOTQ60CgYEA5YjYpEq4L8UUMFkuj+BsS3u0oEBnzuHd"
        + "I9LEHmN+CMPosvabQu5wkJXLuqo2TxRnAznsA8R3pCLkdPGoWMCiWRAsCn979TdY"
        + "QbqO2qvBAD2Q19GtY7lIu6C35/enQWzJUMQE3WW0OvjLzZ0l/9mA2FBRR+3F9A1d"
        + "rBdnmv0c3TcCgYEAi2i+ggVZcqPbtgrLOk5WVGo9F1GqUBvlgNn30WWNTx4zIaEk"
        + "HSxtyaOLTxtq2odV7Kr3LGiKxwPpn/T+Ief+oIp92YcTn+VfJVGw4Z3BezqbR8lA"
        + "Uf/+HF5ZfpMrVXtZD4Igs3I33Duv4sCuqhEvLWTc44pHifVloozNxYfRfU0CgYBN"
        + "HXa7a6cJ1Yp829l62QlJKtx6Ymj95oAnQu5Ez2ROiZMqXRO4nucOjGUP55Orac1a"
        + "FiGm+mC/skFS0MWgW8evaHGDbWU180wheQ35hW6oKAb7myRHtr4q20ouEtQMdQIF"
        + "snV39G1iyqeeAsf7dxWElydXpRi2b68i3BIgzhzebQKBgQCdUQuTsqV9y/JFpu6H"
        + "c5TVvhG/ubfBspI5DhQqIGijnVBzFT//UfIYMSKJo75qqBEyP2EJSmCsunWsAFsM"
        + "TszuiGTkrKcZy9G0wJqPztZZl2F2+bJgnA6nBEV7g5PA4Af+QSmaIhRwqGDAuROR"
        + "47jndeyIaMTNETEmOnms+as17g==";

    public static final RSAPrivateKey DEFAULT_PRIVATE_KEY;
    static
    {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec( Base64.getDecoder().decode( DEFAULT_RSA_PRIVATE_KEY ) );
        try
        {
            DEFAULT_PRIVATE_KEY = (RSAPrivateKey) kf.generatePrivate( spec );
        }
        catch ( InvalidKeySpecException ex )
        {
            throw new IllegalArgumentException( ex );
        }
    }

    public static final KeyPair DEFAULT_RSA_KEY_PAIR = new KeyPair( DEFAULT_PUBLIC_KEY, DEFAULT_PRIVATE_KEY );

    public static final KeyPair DEFAULT_EC_KEY_PAIR = generateEcKeyPair();

    static KeyPair generateEcKeyPair()
    {
        EllipticCurve ellipticCurve = new EllipticCurve( new ECFieldFp(
            new BigInteger( "115792089210356248762697446949407573530086143415290314195533631308867097853951" ) ),
            new BigInteger( "115792089210356248762697446949407573530086143415290314195533631308867097853948" ),
            new BigInteger( "41058363725152142129326129780047268409114441015993725554835256314039467401291" ) );
        ECPoint ecPoint = new ECPoint(
            new BigInteger( "48439561293906451759052585252797914202762949526041747995844080717082404635286" ),
            new BigInteger( "36134250956749795798585127919587881956611106672985015071877198253568414405109" ) );
        ECParameterSpec ecParameterSpec = new ECParameterSpec( ellipticCurve, ecPoint,
            new BigInteger( "115792089210356248762697446949407573529996955224135760342422259061068512044369" ), 1 );

        KeyPair keyPair;
        try
        {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance( "EC" );
            keyPairGenerator.initialize( ecParameterSpec );
            keyPair = keyPairGenerator.generateKeyPair();
        }
        catch ( Exception ex )
        {
            throw new IllegalStateException( ex );
        }

        return keyPair;
    }

    private TestKeys()
    {
    }
}
