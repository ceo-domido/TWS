import com.ib.client.Contract;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Sorts;
import io.jenetics.*;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.stat.DoubleMomentStatistics;
import io.jenetics.util.Factory;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.bson.Document;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.DoubleStream;

import static com.mongodb.client.model.Filters.*;
import static io.jenetics.engine.EvolutionResult.toBestPhenotype;
import static io.jenetics.engine.Limits.bySteadyFitness;

public class MACDSignal extends Indicator {
    private int window = 10;
    private MACDIndicator macdIndicator;

    public MACDSignal(MongoCollection<Document> history, HistoryItem historyItem, int shortPeriod, int longPeriod, int averagePeriod) {
        this.historyData = history;
        this.historyItem = historyItem;
        this.macdIndicator = new MACDIndicator(history,historyItem,shortPeriod,longPeriod,averagePeriod);
    }

    @Override
    public double getValue(String field, long timestamp) {
        return 0;
    }

    @Override
    public String getClassId() {
        return "MACDSignal-"+String.valueOf(window)+"-"+historyItem.getClassId();
    }

    @Override
    public void update() {
        macdIndicator.update();
        System.out.println("Updating values for MACD signal...");
        // start is now
        Calendar startOfPeriod = Calendar.getInstance(TimeZone.getTimeZone("EST"));
        // initialize current value
        SignalItem signalItem = new SignalItem(this.getClassId());
        // find last EMA record
        Document lastValue = historyData.find(eq("class",signalItem.getClassId()))
                .sort(Sorts.descending("timestamp")).first();
        // if last record exist then start from it else start from some months later
        if (lastValue != null) {
            startOfPeriod.setTimeInMillis(lastValue.getLong("timestamp"));
            signalItem = SignalItem.createFromDocument(lastValue);
        } else {
            // compute start value
            startOfPeriod.add(Calendar.MONTH, -Global.HISTORY_MONTH_DEPTH);
        }
        CircularFifoQueue<MACDItem> macdItems = new CircularFifoQueue<>();
        CircularFifoQueue<EMAItem> shortEmaItems = new CircularFifoQueue<>();
        CircularFifoQueue<EMAItem> longEmaItems = new CircularFifoQueue<>();
        MongoCursor<Document> cursor = historyData
                .find(and(eq("class",macdIndicator.getClassId()),gt("timestamp",startOfPeriod.getTimeInMillis())))
                .sort(Sorts.ascending("timestamp")).iterator();
        while (cursor.hasNext()) {
            Document doc = cursor.next();
            MACDItem newItem = MACDItem.createFromDocument(doc);
            macdItems.add(newItem);
            Document shortEMADoc = historyData
                    .find(and(eq("class",macdIndicator.shortEMA.getClassId()),eq("timestamp",newItem.timestamp)))
                    .first();
            Document longEMADoc = historyData
                    .find(and(eq("class",macdIndicator.longEMA.getClassId()),eq("timestamp",newItem.timestamp)))
                    .first();
            if (shortEMADoc != null && longEMADoc != null) {
                EMAItem shortEMA = EMAItem.createFromDocument(shortEMADoc);
                EMAItem longEMA = EMAItem.createFromDocument(longEMADoc);
                shortEmaItems.add(shortEMA);
                longEmaItems.add(longEMA);

                historyData.insertOne(signalItem.getDocument());
                System.out.println("EMA value updated for date ");
            }
        }
    }

    @Override
    public Document getDocument() {
        Document doc = new Document();
        doc.append("_id", getClassId());
        doc.append("barSize", historyItem.barSize);
        doc.append("dataType", historyItem.dataType);
        doc.append("exchange", historyItem.contract.exchange());
        doc.append("secType", historyItem.contract.secType());
        doc.append("currency", historyItem.contract.currency());
        doc.append("symbol", historyItem.contract.symbol());
        doc.append("window", window);
        doc.append("macdIndicator", macdIndicator.getDocument());
        doc.append("indicatorType", "MACDSignal");
        doc.append("class",getClassId());
        return doc;
    }

    public static MACDSignal createFromDocument(Document doc, MongoCollection<Document> history) {
        HistoryItem item = new HistoryItem();
        item.barSize = doc.getString("barSize");
        item.dataType = doc.getString("dataType");
        item.contract = new Contract();
        item.contract.exchange(doc.getString("exchange"));
        item.contract.secType(doc.getString("secType"));
        item.contract.currency(doc.getString("currency"));
        item.contract.symbol(doc.getString("symbol"));
        int window = doc.getInteger("window");
        MACDIndicator macd = MACDIndicator.createFromDocument(doc.get("macdIndicator", Document.class), history);
        MACDSignal result = new MACDSignal(history, item, 0,0,0);
        result.macdIndicator = macd;
        return result;
    }

    public double testProfit(Double[] price, Double[] buyProfit, Double[] sellProfit, int shortPeriod, int longPeriod, int averagePeriod) {
        double sA = 2.0/(1+shortPeriod);
        double lA = 2.0/(1+longPeriod);
        double aA = 2.0/(1+averagePeriod);
        double sEMA = price[0];
        double lEMA = price[0];
        double MACD = 0.0;
        double prevMACD;
        double result = 0.0;
        int end = Math.min(Math.min(price.length,buyProfit.length),sellProfit.length);
        for (int i=1; i<end; i++) {
            sEMA = price[i]*sA + sEMA*(1-sA);
            lEMA = price[i]*lA + lEMA*(1-lA);
            prevMACD = MACD;
            MACD = (sEMA-lEMA)*aA + MACD*(1-aA);
            if (prevMACD<0 && MACD>0) {
                result += buyProfit[i]-0.005;
            }
            if (prevMACD>0 && MACD<0) {
                result += sellProfit[i]-0.005;
            }
        }
        return result;
    }

    public ArrayList<HashMap<String, Double[]>> prepareTrainingSets(String[] dataClass, String[] profitClass) {
        ArrayList<HashMap<String, Double[]>> trainingSets = new ArrayList<>();
        StandardDeviation stdObj = new StandardDeviation();
        Mean meanObj = new Mean();

        int SETS_NUM = Math.min(dataClass.length, profitClass.length);
        for (int k = 0; k < SETS_NUM; k++) {
            //Prepare training sets
            ArrayList<Double> pricesArray = new ArrayList<>();
            MongoCursor<Document> cursor = historyData
                    .find(eq("class", dataClass[k]))
                    .sort(Sorts.ascending("timestamp")).iterator();
            while (cursor.hasNext()) {
                pricesArray.add(cursor.next().getDouble("close"));
            }
            ArrayList<Double> buyProfitsArray = new ArrayList<>();
            ArrayList<Double> sellProfitsArray = new ArrayList<>();
            cursor = historyData
                    .find(eq("class", profitClass[k]))
                    .sort(Sorts.ascending("timestamp")).iterator();
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                buyProfitsArray.add(doc.getDouble("buyProfit"));
                sellProfitsArray.add(doc.getDouble("sellProfit"));
            }

            Double[] prices = pricesArray.toArray(new Double[pricesArray.size()]);
            Double[] buyProfits = buyProfitsArray.toArray(new Double[buyProfitsArray.size()]);
            Double[] sellProfits = sellProfitsArray.toArray(new Double[sellProfitsArray.size()]);
            HashMap<String, Double[]> set = new HashMap<>();
            set.put("prices", prices);
            set.put("buys", buyProfits);
            set.put("sells", sellProfits);
            set.put("mean", new Double[]{meanObj.evaluate(ArrayUtils.toPrimitive(prices))});
            trainingSets.add(set);
            System.out.printf("%s data set is loaded%n", dataClass[k]);
        }
        System.out.println("Data preparation is done");
        return trainingSets;
    }

    private class FitnessFunction implements Function<Genotype<IntegerGene>,Double> {
        private ArrayList<HashMap<String, Double[]>> trainingSets;

        public FitnessFunction(ArrayList<HashMap<String, Double[]>> trainingSets) {
            this.trainingSets = trainingSets;
        }

        public double[] profit(Genotype<IntegerGene> gt) {
            double[] profit = new double[trainingSets.size()];
            for (int k=0; k<trainingSets.size(); k++) {
                profit[k] += testProfit(trainingSets.get(k).get("prices"),
                        trainingSets.get(k).get("buys"),
                        trainingSets.get(k).get("sells"),
                        gt.get(0,0).intValue(),
                        gt.get(0,1).intValue(),
                        gt.get(0,2).intValue());
            }
            return profit;
        }

        @Override
        public Double apply(Genotype<IntegerGene> gt) {
            final double MAX_PROFIT = 0.5;
            int SETS_NUM = trainingSets.size();
            double fitness = 0.0;
            for (int xI=-1; xI<1; xI++) {
                for (int yI=-1; yI<1; yI++) {
                    for (int zI=-1; zI<1; zI++) {
                        int xP = (int)((1+xI*0.01)*gt.get(0,0).intValue());
                        int yP = (int)((1+yI*0.01)*gt.get(0,1).intValue());
                        int zP = (int)((1+zI*0.01)*gt.get(0,2).intValue());
                        for (int k=0; k<SETS_NUM; k++) {
                            double fit = testProfit(trainingSets.get(k).get("prices"),
                                    trainingSets.get(k).get("buys"),
                                    trainingSets.get(k).get("sells"),
                                    xP, yP, zP)/9/
                                    trainingSets.get(k).get("mean")[0];
                            fitness += fit > MAX_PROFIT ? MAX_PROFIT : fit;
                        }
                    }
                }
            }
            return fitness;
        }
    }

    public void newTrain(String[] dataClass, String[] profitClass) {
        ArrayList<HashMap<String, Double[]>> trainingSets = prepareTrainingSets(dataClass,profitClass);
        Factory<Genotype<IntegerGene>> gtf = Genotype.of(IntegerChromosome.of(
                IntegerGene.of(10,17280),
                IntegerGene.of(10,37440),
                IntegerGene.of(10,12960)));
        FitnessFunction ft = new FitnessFunction(trainingSets);
        final Engine<IntegerGene,Double> engine = Engine
                .builder(ft, gtf)
                .populationSize(1500)
                .optimize(Optimize.MAXIMUM)
                .alterers(
                new Mutator<>(0.15),
                new MeanAlterer<>(0.6))
                .build();
        final EvolutionStatistics<Double, DoubleMomentStatistics> statistics =
                EvolutionStatistics.ofNumber();
        final Phenotype<IntegerGene, Double> result = engine.stream()
                .limit(bySteadyFitness(10))
                // Terminate the evolution after maximal 100 generations.
                .limit(100)
                .peek(statistics)
                .collect(toBestPhenotype());
        System.out.printf("Best result is %f with genes %d, %d, %d and total profit ", result.getFitness(),
                result.getGenotype().get(0,0).intValue(),
                result.getGenotype().get(0,1).intValue(),
                result.getGenotype().get(0,2).intValue());
        for (int k=0; k<trainingSets.size(); k++) {
            System.out.printf("%f ", ft.profit(result.getGenotype())[k]);
        }
        System.out.printf("%n");
        System.out.println(statistics);
    }
        // Trailing stop 0.5%
        // 0,103569 with genes 3650, 5964, 4673 and total profit 1,020000 5,605000 5,490000 1,640000
        // Trailing stop 1.0%
        // 0,169420 with genes 10523, 13526, 12804 and total profit 0,460000 0,105000 13,810000 7,425000
        // 0,169715 with genes 10443, 14152, 12332 and total profit 0,500000 0,135000 10,715000 7,350000
        // 0,167345 with genes 10717, 13765, 12480 and total profit 0,495000 -0,090000 7,150000 6,470000
        // 37,787175 with genes 174, 86, 3056 and total profit -10,240000 -6,790000 -62,960000 41,590000 -29,400000 -13,780000 -138,645000 37,135000 1139,305000 -36,250000
        // 8,208362 with genes 3719, 25, 11464 and total profit -0,910000 -3,580000 2,670000 11,420000 -2,500000 9,710000 -20,035000 -0,865000 235,065000 0,285000


           // Trailing stop 1.0%
        // (9822, 34818, 1653) with total profit 17,355000
        // (1370, 32873, 10991) with total profit 20,005000
        // (13173, 29243, 1129) with total profit 17,760000
        // (276, 997, 147) with total profit 17,570000 (mean 0,278200 deviation 4,353703)
        // (50, 14770, 17) with total profit 21,820000 (mean 0,190760 deviation 4,044192)
        // Trailing stop 0.5%
        // (340, 3284, 210) with total profit 18,355000
        // (34, 23125, 20) with total profit 30,065000
        // (15, 23317, 11) with total profit 27,865000
        // (37, 23942, 16) with total profit 25,745000
        // Trailing stop 2.0%
        // (1254, 10224, 236) with total profit 18,255000
        // (1074, 8714, 628) with total profit 18,950000
        // (1241, 10016, 228) with total profit 18,805000 and deviation 5,365417

}
