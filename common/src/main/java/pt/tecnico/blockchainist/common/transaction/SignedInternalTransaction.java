package pt.tecnico.blockchainist.common.transaction;


public class SignedInternalTransaction {
    
    private InternalTransaction transaction;
    private byte[] signature;

    public SignedInternalTransaction(
        InternalTransaction transaction,
        byte[] signature
    ){
        this.transaction = transaction;
        this.signature = signature;
    }

    public byte[] getSignature(){ return this.signature; }
    public InternalTransaction getInternalTransaction(){ return this.transaction; }
}
