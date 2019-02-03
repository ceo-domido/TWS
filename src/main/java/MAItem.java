import com.ib.client.Bar;
import org.bson.Document;

public class MAItem implements DateItem {
    public double maOpen = 0.0;
    public double maClose = 0.0;
    public double maHigh = 0.0;
    public double maLow = 0.0;
    public double maVolume = 0.0;
    public double maWAP = 0.0;
    public double maCount = 0.0;
    public long timestamp = 0;
    public String contract;
    public int period = 1;

    public MAItem(String contract, int period) {
        this.contract = contract;
        this.period = period;
    }

    public static MAItem createFromDocument(Document document) {
        String cont = document.getString("contract");
        int period = document.getInteger("period");
        MAItem item = new MAItem(cont,period);
        item.maOpen = document.getDouble("open");
        item.maClose = document.getDouble("close");
        item.maHigh = document.getDouble("high");
        item.maLow = document.getDouble("low");
        item.maVolume = document.getDouble("volume");
        item.maWAP = document.getDouble("wap");
        item.maCount = document.getDouble("count").floatValue();
        item.timestamp = document.getLong("timestamp");
        return item;
    }

    public Document getDocument() {
        Document doc = new Document();
        doc.append("_id", getId());
        doc.append("contract", contract);
        doc.append("class",getClassId());
        doc.append("period", period);
        doc.append("open", maOpen);
        doc.append("close", maClose);
        doc.append("high", maHigh);
        doc.append("low", maLow);
        doc.append("volume", maVolume);
        doc.append("wap", maWAP);
        doc.append("count", maCount);
        doc.append("timestamp", timestamp);
        return doc;
    }

    public String getClassId() {
        return "MA-"+String.valueOf(period)+"-"+contract;
    }

    public String getId() {
        return "MA-"+String.valueOf(period)+"-"+contract+"-"+String.valueOf(timestamp);
    }

    public MAItem add(Bar data) {
        maOpen += data.open()/period;
        maClose += data.close()/period;
        maHigh += data.high()/period;
        maLow += data.low()/period;
        maVolume += data.volume()/period;
        maWAP += data.wap()/period;
        maCount += data.count()/period;
        return this;
    }

    public MAItem add(MAItem data) {
        maOpen += data.maOpen;
        maClose += data.maClose;
        maHigh += data.maHigh;
        maLow += data.maLow;
        maVolume += data.maVolume;
        maWAP += data.maWAP;
        maCount += data.maCount;
        return this;
    }

    public MAItem sub(Bar data) {
        maOpen -= data.open()/period;
        maClose -= data.close()/period;
        maHigh -= data.high()/period;
        maLow -= data.low()/period;
        maVolume -= data.volume()/period;
        maWAP -= data.wap()/period;
        maCount -= data.count()/period;
        return this;
    }

    public MAItem sub(MAItem data) {
        maOpen -= data.maOpen;
        maClose -= data.maClose;
        maHigh -= data.maHigh;
        maLow -= data.maLow;
        maVolume -= data.maVolume;
        maWAP -= data.maWAP;
        maCount -= data.maCount;
        return this;
    }
}
