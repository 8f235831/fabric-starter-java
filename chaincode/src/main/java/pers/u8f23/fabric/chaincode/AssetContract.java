package pers.u8f23.fabric.chaincode;

import com.owlike.genson.Genson;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.*;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import pers.u8f23.fabric.chaincode.api.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Contract(
        name = BuildConfig.CHAINCODE_NAME,
        info = @Info(
                title = BuildConfig.CHAINCODE_TITLE,
                description = BuildConfig.CHAINCODE_DESCRIPTION,
                version = BuildConfig.CHAINCODE_VERSION)
)
@Default
public final class AssetContract implements ContractInterface, ContractApi {
    private final Genson genson = new Genson();

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    @Override
    public String createAsset(final Context context, final String value) {
        ChaincodeStub stub = context.getStub();
        Instant time = stub.getTxTimestamp();
        String clientId = context.getClientIdentity().getId();
        String assetId = UidUtils.generateUid(time, clientId, value);
        if (existAsset(stub, assetId)) {
            String msg = "Conflicted to generate asset unique id. Please retry later.";
            throw new ChaincodeException(msg, msg);
        }
        long time64 = time.toEpochMilli();
        Asset asset = new Asset(assetId, clientId, clientId, time64, -1, -1, value);
        String sortedJson = genson.serialize(asset);
        stub.putStringState(assetId, sortedJson);
        return genson.serialize(new Response<>(asset));
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    @Override
    public String updateAsset(final Context context, final String assetId, final String value) {
        ChaincodeStub stub = context.getStub();
        String assetJson = stub.getStringState(assetId);
        if (assetJson == null || assetJson.isEmpty()) {
            return genson.serialize(new Response<>(-1, "ERR_ASSET_NOT_EXIST"));
        }
        Asset asset = genson.deserialize(assetJson, Asset.class);
        String clientId = context.getClientIdentity().getId();
        if (!Objects.equals(asset.getOwnerId(), clientId)) {
            return genson.serialize(new Response<>(-1, "ERR_NOT_ASSET_OWNER"));
        }
        long time = stub.getTxTimestamp().toEpochMilli();
        Asset newAsset = new Asset(
                asset.getId(),
                asset.getCreatorId(),
                asset.getOwnerId(),
                asset.getCreateTime(),
                asset.getLastTransferTime(),
                time,
                value
        );
        stub.putStringState(assetId, genson.serialize(newAsset));
        return genson.serialize(new Response<>());
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    @Override
    public String deleteAsset(final Context context, final String assetId) {
        ChaincodeStub stub = context.getStub();
        String assetJson = stub.getStringState(assetId);
        if (assetJson == null || assetJson.isEmpty()) {
            return genson.serialize(new Response<>(-1, "ERR_ASSET_NOT_EXIST"));
        }
        Asset asset = genson.deserialize(assetJson, Asset.class);
        String clientId = context.getClientIdentity().getId();
        if (!Objects.equals(asset.getOwnerId(), clientId)) {
            return genson.serialize(new Response<>(-1, "ERR_NOT_ASSET_OWNER"));
        }
        stub.delState(assetId);
        return genson.serialize(new Response<>());
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    @Override
    public String findAsset(final Context context, final String assetId) {
        ChaincodeStub stub = context.getStub();
        String assetJson = stub.getStringState(assetId);
        if (assetJson == null || assetJson.isEmpty()) {
            return genson.serialize(new Response<>());
        }
        Response<Asset> res = new Response<>(genson.deserialize(assetJson, Asset.class));
        return genson.serialize(res);
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    @Override
    public String findAllAsset(final Context context) {
        ChaincodeStub stub = context.getStub();
        QueryResultsIterator<KeyValue> resultPairs = stub.getStateByRange("", "");
        List<Asset> assets = new ArrayList<>();
        for (KeyValue pair : resultPairs) {
            try {
                assets.add(genson.deserialize(pair.getStringValue(), Asset.class));
            } catch (Exception e) {
                String msg = String.format("Failed to decode asset JSON with id \"%s\", JSON:%s", pair.getKey(), pair.getStringValue());
                throw new ChaincodeException(msg);
            }
        }
        return genson.serialize(new Response<>(assets));
    }

    private boolean existAsset(final ChaincodeStub stub, final String assetId) {
        byte[] bytes = stub.getState(assetId);
        return bytes != null && bytes.length > 0;
    }
}
