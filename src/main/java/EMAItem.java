import com.ib.client.Bar;
import org.bson.Document;

public class EMAItem implements DateItem {
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
    private boolean first = true;

    public EMAItem(String cont, int period) {
        this.contract = cont;
        this.period = period;
    }

    public static EMAItem createFromDocument(Document document) {
        String cont = document.getString("class");
        int period = document.getInteger("period");
        EMAItem item = new EMAItem(cont,period);
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


    public EMAItem sum(Bar data) {
        double a;
        a = 2.0 / (period + 1);
        maOpen = maOpen==0 ? data.open() : data.open()*a+maOpen*(1-a);
        maClose = maClose==0 ? data.close() : data.close()*a+maClose*(1-a);
        maHigh = maHigh==0 ? data.high() : data.high()*a+maHigh*(1-a);
        maLow = maLow==0 ? data.low() : data.low()*a+maLow*(1-a);
        maVolume = maVolume==0 ? data.volume() : data.volume()*a+maVolume*(1-a);
        maWAP = maWAP==0 ? data.wap() : data.wap()*a+maWAP*(1-a);
        maCount = maCount==0 ? data.count() : data.count()*a+maCount*(1-a);
        return this;
    }

    public EMAItem sum(EMAItem data) {
        double a;
        a = 2.0 / (period + 1);
        maOpen = maOpen==0 ? data.maOpen : data.maOpen*a+maOpen*(1-a);
        maClose = maClose==0 ? data.maClose : data.maClose*a+maClose*(1-a);
        maHigh = maHigh==0 ? data.maHigh : data.maHigh*a+maHigh*(1-a);
        maLow = maLow==0 ? data.maLow : data.maLow*a+maLow*(1-a);
        maVolume = maVolume==0 ? data.maVolume : data.maVolume*a+maVolume*(1-a);
        maWAP = maWAP==0 ? data.maWAP : data.maWAP*a+maWAP*(1-a);
        maCount = maCount==0 ? data.maCount : data.maCount*a+maCount*(1-a);
        return this;
    }

    public EMAItem sub(EMAItem data) {
        maOpen -= data.maOpen;
        maClose -= data.maClose;
        maHigh -= data.maHigh;
        maLow -= data.maLow;
        maVolume -= data.maVolume;
        maWAP -= data.maWAP;
        maCount -= data.maCount;
        return this;
    }

    public EMAItem add(EMAItem data) {
        maOpen += data.maOpen;
        maClose += data.maClose;
        maHigh += data.maHigh;
        maLow += data.maLow;
        maVolume += data.maVolume;
        maWAP += data.maWAP;
        maCount += data.maCount;
        return this;
    }

    public String getClassId() {
        return contract;
    }

    public String getId() {
        return contract +"-"+String.valueOf(timestamp);
    }
}
