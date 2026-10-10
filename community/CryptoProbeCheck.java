import org.hyperscan.diagnostics.CryptoProbe;
import org.hyperscan.diagnostics.LogSummary;
class CryptoProbeCheck {
    public static void main(String[] args) {
        String result = CryptoProbe.stsSummary("PRIVATE_ID", "PRIVATE_SECRET", "PRIVATE_CREDENTIAL");
        if (result.contains("PRIVATE") || !result.contains("accessId_present=true") || !result.contains("credential_present=true")) throw new AssertionError(result);
        String empty = CryptoProbe.stsSummary(null, "", "PRIVATE_CREDENTIAL");
        if (!empty.contains("accessId_present=false") || !empty.contains("accessSecret_present=false")) throw new AssertionError(empty);
        String captured = LogSummary.summarize("W/HyperScanCrypto: " + result);
        if (captured == null || !captured.contains("response_code=0") || !captured.contains("credential_present=true")) throw new AssertionError(captured);
        System.out.println("STS result presence and credential non-disclosure checks passed.");
    }
}
