import com.ib.client.Bar;
import org.bson.Document;

public class ProfitItem {
    public double buyProfit = 0.0;
    public double sellProfit = 0.0;
    public double buyROI = 0.0;
    public double sellROI = 0.0;
    public long timestamp = 0;
    public String contract;

    public ProfitItem(String cont) {
        this.contract = cont;
    }

    public static ProfitItem createFromDocument(Document document) {
        String cont = document.getString("contract");
        ProfitItem item = new ProfitItem(cont);
        item.buyProfit = document.getDouble("buyProfit");
        item.sellProfit = document.getDouble("sellProfit");
        item.buyROI = document.getDouble("buyROI");
        item.sellROI = document.getDouble("sellROI");
        item.timestamp = document.getLong("timestamp");
        return item;
    }

    public Document getDocument() {
        Document doc = new Document();
        doc.append("_id", getId());
        doc.append("class",getClassId());
        doc.append("buyProfit", buyProfit);
        doc.append("sellProfit", sellProfit);
        doc.append("buyROI", buyROI);
        doc.append("sellROI", sellROI);
        doc.append("timestamp", timestamp);
        doc.append("contract",contract);
        return doc;
    }

    public String getClassId() {
        return contract;
    }

    public String getId() {
        return contract +"-"+String.valueOf(timestamp);
    }
}
