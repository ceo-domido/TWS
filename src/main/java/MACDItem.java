import com.ib.client.Bar;
import org.bson.Document;

public class MACDItem extends EMAItem {


    public MACDItem(String contract, int period) {
        super(contract,period);
    }

    public static MACDItem createFromDocument(Document document) {
        String cont = document.getString("class");
        int period = document.getInteger("period");
        MACDItem item = new MACDItem(cont,period);
        item.maOpen = document.getDouble("open");
        item.maClose = document.getDouble("close");
        item.maHigh = document.getDouble("high");
        item.maLow = document.getDouble("low");
        item.maVolume = document.getDouble("volume");
        item.maWAP = document.getDouble("wap");
        item.maCount = document.getDouble("count").floatValue();
        return item;
    }



    public String getId() {
        return contract+"-"+String.valueOf(timestamp);
    }
}
