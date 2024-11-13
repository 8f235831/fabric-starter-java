package pers.u8f23.fabric.app;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.client.*;
import org.hyperledger.fabric.client.identity.*;
import org.hyperledger.fabric.protos.gateway.ErrorDetail;
import pers.u8f23.fabric.app.api.Asset;
import pers.u8f23.fabric.app.api.ContractApi;
import pers.u8f23.fabric.app.api.ProposedSubmit;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
public class Main {
    private static final String MSP_ID = "Org1MSP";
    private static final String CHANNEL_NAME = BuildConfig.CHANNEL_NAME;
    private static final String CHAINCODE_NAME = BuildConfig.CHAINCODE_NAME;

    private static final Path CRYPTO_HOME_PATH = Paths.get(BuildConfig.CRYPTO_DIR);
    // Path to crypto materials.
    private static final Path CRYPTO_PATH = CRYPTO_HOME_PATH.resolve(Paths.get("peerOrganizations/org1.example.com"));
    // Path to user certificate.
    private static final Path CERT_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/signcerts"));
    // Path to user private key directory.
    private static final Path KEY_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/keystore"));
    // Path to peer tls certificate.
    private static final Path TLS_CERT_PATH = CRYPTO_PATH.resolve(Paths.get("peers/peer0.org1.example.com/tls/ca.crt"));

    // Gateway peer end point.
    private static final String PEER_ENDPOINT = String.format("%s:7051", BuildConfig.GATEWAY_HOST);
    private static final String OVERRIDE_AUTH = "peer0.org1.example.com";

    public static void main(String[] args) throws Exception {
        ManagedChannel channel = newGrpcConnection();

        Gateway.Builder builder = Gateway.newInstance()
                .identity(newIdentity())
                .signer(newSigner())
                .hash(Hash.SHA256)
                .connection(channel)
                // Default timeouts for different gRPC calls
                .evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .endorseOptions(options -> options.withDeadlineAfter(15, TimeUnit.SECONDS))
                .submitOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .commitStatusOptions(options -> options.withDeadlineAfter(1, TimeUnit.MINUTES));

        try (Gateway gateway = builder.connect()) {
            log.info("gateway connected");
            chaincodeOperations(gateway);
        } catch (GatewayException e) {
            log.error("GatewayException caught, detail size: {}", e.getDetails().size(), new RuntimeException(e));
            List<ErrorDetail> details = e.getDetails();
            details.forEach(detailItem -> log.error("error detail: msg: {}", detailItem.getMessage()));
        } catch (Exception e) {
            log.error("Exception caught", new RuntimeException(e));
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            log.info("gateway shutdown");
        }
    }

    private static void chaincodeOperations(Gateway gateway) throws Exception {
        Network network = gateway.getNetwork(CHANNEL_NAME);
        Contract contract = network.getContract(CHAINCODE_NAME);
        ContractApi api = new ContractApi(contract, new Gson());

        log.info("*** Result findAllAsset(): {}", api.findAllAsset());
        ProposedSubmit<Asset> proposedSubmit = api.createAsset("test wgvwr");
        log.info("*** Result proposedSubmit.blockingGetEvaluatedRes(): {}", proposedSubmit.blockingGetEvaluatedRes());
        log.info("*** Result proposedSubmit.blockingGetSubmitStatus(): {}", proposedSubmit.blockingGetSubmitStatus());
        log.info("*** Result findAllAsset(): {}", api.findAllAsset());
    }

    private static ManagedChannel newGrpcConnection() throws IOException {
        new Gson().fromJson(new InputStreamReader(new ByteArrayInputStream("".getBytes())), new TypeToken<String>() {
        });
        ChannelCredentials credentials = TlsChannelCredentials.newBuilder()
                .trustManager(TLS_CERT_PATH.toFile())
                .build();
        return Grpc.newChannelBuilder(PEER_ENDPOINT, credentials)
                .overrideAuthority(OVERRIDE_AUTH)
                .build();
    }

    private static Identity newIdentity() throws IOException, CertificateException {
        try (Reader certReader = Files.newBufferedReader(getFirstFilePath(CERT_DIR_PATH))) {
            X509Certificate certificate = Identities.readX509Certificate(certReader);
            return new X509Identity(MSP_ID, certificate);
        }
    }

    private static Signer newSigner() throws IOException, InvalidKeyException {
        try (Reader keyReader = Files.newBufferedReader(getFirstFilePath(KEY_DIR_PATH))) {
            PrivateKey privateKey = Identities.readPrivateKey(keyReader);
            return Signers.newPrivateKeySigner(privateKey);
        }
    }

    private static Path getFirstFilePath(Path dirPath) throws IOException {
        try (Stream<Path> keyFiles = Files.list(dirPath)) {
            return keyFiles.findFirst().orElseThrow();
        }
    }
}
