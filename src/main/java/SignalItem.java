import com.ib.client.Bar;
import org.bson.Document;

public class SignalItem implements DateItem {
    public double buySignal = 0.0;
    public double sellSignal = 0.0;
    public long timestamp = 0;
    public String contract;

    public SignalItem(String contract) {
        this.contract = contract;
    }

    public static SignalItem createFromDocument(Document document) {
        String cont = document.getString("contract");
        int period = document.getInteger("period");
        SignalItem item = new SignalItem(cont);
        item.buySignal = document.getDouble("buy");
        item.sellSignal = document.getDouble("sell");
        item.timestamp = document.getLong("timestamp");
        return item;
    }

    public Document getDocument() {
        Document doc = new Document();
        doc.append("_id", getId());
        doc.append("contract", contract);
        doc.append("class",getClassId());
        doc.append("sell", sellSignal);
        doc.append("buy", buySignal);
        doc.append("timestamp", timestamp);
        return doc;
    }

    public String getClassId() {
        return contract;
    }

    public String getId() {
        return contract+"-"+String.valueOf(timestamp);
    }
}
