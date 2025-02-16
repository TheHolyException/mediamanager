package de.theholyexception.mediamanager.handler;

import de.theholyexception.holyapi.datastorage.json.JSONObjectContainer;
import de.theholyexception.holyapi.datastorage.sql.interfaces.DataBaseInterface;
import de.theholyexception.holyapi.datastorage.sql.interfaces.MySQLInterface;
import de.theholyexception.holyapi.datastorage.sql.interfaces.SQLiteInterface;
import de.theholyexception.mediamanager.AniworldHelper;
import de.theholyexception.mediamanager.MediaManager;
import de.theholyexception.mediamanager.configuration.ConfigJSON;
import de.theholyexception.mediamanager.models.aniworld.Anime;
import de.theholyexception.mediamanager.models.aniworld.Episode;
import de.theholyexception.mediamanager.models.SettingMetadata;
import de.theholyexception.mediamanager.models.aniworld.Season;
import de.theholyexception.mediamanager.settings.SettingProperty;
import lombok.extern.slf4j.Slf4j;
import me.kaigermany.ultimateutils.StaticUtils;
import me.kaigermany.ultimateutils.networking.websocket.WebSocketBasic;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class AutoLoaderHandler extends Handler {

    private JSONObjectContainer subscriptions;
    private SettingProperty<Integer> spCheckIntervalMin;
    private final AtomicInteger checkIntervalMS = new AtomicInteger();
    private final List<Anime> subscribedAnimes = Collections.synchronizedList(new ArrayList<>());
    private SettingProperty<Integer> spURLResolverThreads;
    private ConfigJSON dataFile;
    private DataBaseInterface sqlite;

    public AutoLoaderHandler(String targetSystem) {
        super(targetSystem);
    }

    @Override
    public void loadConfigurations() {
        super.loadConfigurations();

        spURLResolverThreads = new SettingProperty<>(new SettingMetadata("urlResolverThreads", false));
        spURLResolverThreads.addSubscriber(value -> Episode.urlResolver.updateExecutorService(Executors.newFixedThreadPool(value)));
        spURLResolverThreads.setValue(handlerConfiguration.get("urlResolverThreads", 10, Integer.class));

        String dataFilePath = handlerConfiguration.get("dataFile", "./autoloader.data.json", String.class);
        this.dataFile = new ConfigJSON(new File(dataFilePath));
        this.dataFile.createNewIfNotExists();
        this.dataFile.loadConfig();

        subscriptions = dataFile.getJson().getObjectContainer("subscriptions", new JSONObjectContainer());
        spCheckIntervalMin = new SettingProperty<>(new SettingMetadata("checkIntervalMin", false));
        spCheckIntervalMin.addSubscriber(value -> {
            if (!subscriptions.getRaw().isEmpty())
                checkIntervalMS.set(value*60*1000 / subscriptions.getRaw().size());
            else
                checkIntervalMS.set(Integer.MAX_VALUE);
        });
        spCheckIntervalMin.setValue(handlerConfiguration.get("checkIntervalMin", 5, Integer.class));

    }

    @Override
    public void initialize() {
        DefaultHandler defaultHandler = (DefaultHandler) MediaManager.getInstance().getHandlers().get("default");
        Anime.setBaseDirectory(new File(defaultHandler.getTargets().get("stream-animes").path()));

        loadDatabase();

        try {
            subscribedAnimes.clear();
            subscribedAnimes.addAll(Anime.loadFromDB(sqlite));
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        printTableInfo("anime", "season", "episode");
        //addSubscriber("https://aniworld.to/anime/stream/dr-stone", 1);
        subscribedAnimes.forEach(Anime::scanDirectoryForExistingEpisodes);
        subscribedAnimes.forEach(a -> System.out.println("unloaded: " + a.getUnloadedEpisodeCount()));
        subscribedAnimes.forEach(a -> {
            if (a.isDeepDirty()) {
                System.out.println("WRITE TO DB");
                a.writeToDB(sqlite);
            } else {
                System.out.println("Not Deep Dirty");
            }
        });
        sqlite.getExecutorHandler().awaitGroup(-1);

        //startThread();

        printTableInfo("anime", "season", "episode");

        System.out.println("Loading animes:");
        List<Anime> dbanimes = new ArrayList<>();
        try {
            dbanimes = Anime.loadFromDB(sqlite);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        Anime a = dbanimes.get(0);
        System.out.println(dbanimes.size());
        System.out.println(a.getSeasonList().size());
        System.out.println(a.getEpisodeCount());

        System.out.println(a.getDirectory());
        System.out.println("Unloaded: " + a.getUnloadedEpisodeCount());
    }

    @Override
    public void handleCommand(WebSocketBasic socket, String command, JSONObjectContainer content) {
        super.handleCommand(socket, command, content);

    }

    private void startThread() {
        Thread t = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    int checkInterval = checkIntervalMS.get();

                    ResultSet rs = sqlite.executeQuery("select * from anime");
                    while (rs.next()) {
                        int id = rs.getInt("nKey");
                        Optional<Anime> optAnime = subscribedAnimes.stream().filter(a -> a.getId() == id).findFirst();
                        Anime anime;
                        if (optAnime.isEmpty())
                            anime = new Anime(rs);
                        else
                            anime = optAnime.get();

                        anime.loadMissingEpisodes();

                        if (anime.isDirty()) {
                            anime.writeToDB(sqlite);
                        }
                    }
                    Thread.sleep(checkInterval);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    try {Thread.sleep(2000);} catch (Exception ee) {ee.printStackTrace();}
                    //log.error(ex.getMessage());
                }
            }
        });
        t.setName("AutoLoader");
        t.start();
    }

    public void addSubscriber(String url, int languageId) {
        if (subscribedAnimes.stream().anyMatch(a -> a.getUrl().equals(url)))
            throw new IllegalStateException("Failed to subscribe to " + url + " this url is already subscribed!");

        System.out.println("Adding subscriber " + url);
        String title = AniworldHelper.getAnimeTitle(url);
        System.out.println("Resolved title: " + title);

        Anime anime = new Anime(-1, languageId, title, url);
        anime.loadMissingEpisodes();
        subscribedAnimes.add(anime);
        spCheckIntervalMin.trigger();
        anime.writeToDB(sqlite);

        sqlite.getExecutorHandler().awaitGroup(-1);







    }


    /*public void removeSubscriber(String key) {
        seriesSubscribers.remove(key);
        spCheckIntervalMin.trigger();
    }*/

    private void loadDatabase() {
        try {
            File f = new File("./datastore.sqlite");
            if (!f.exists()) f.createNewFile();

            //sqlite = new SQLiteInterface(f);
            JSONObjectContainer mysqlConfig = handlerConfiguration.getObjectContainer("mysql");
            sqlite = new MySQLInterface(
                    mysqlConfig.get("host", String.class),
                    mysqlConfig.get("port", Integer.class),
                    mysqlConfig.get("username", String.class),
                    mysqlConfig.get("password", String.class),
                    mysqlConfig.get("database", String.class));
            sqlite.asyncDataSettings(1);
            sqlite.connect();

            if (1 == 2) {
                sqlite.execute("drop table if exists anime");
                sqlite.execute("drop table if exists season");
                sqlite.execute("drop table if exists episode");

                sqlite.execute("""
                        create table if not exists anime        (nKey           integer primary key,
                                                                 nLanguageId    integer,
                                                                 szTitle        varchar(255),
                                                                 szURL          varchar(255));
                        """);
                sqlite.execute("""                                                    
                        create table if not exists season       (nKey           integer primary key,
                                                                 nAnimeLink     integer,
                                                                 nSeasonNumber  integer,
                                                                 szURL          varchar(255));
                        """);
                sqlite.execute("""                                                     
                        create table if not exists episode      (nKey           integer primary key,
                                                                 nSeasonLink    integer,
                                                                 nEpisodeNumber integer,
                                                                 szTitle        varchar(255),
                                                                 szURL          varchar(255),
                                                                 bLoaded        int);
                        """);

                sqlite.getExecutorHandler().awaitGroup(-1);
            }


            Anime.setCurrentID(getCurrentId("anime"));
            Season.setCurrentID(getCurrentId("season"));


        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void printTableInfo(String... tables) {
        for (String table : tables) {
            try {
                ResultSet rs = sqlite.executeQuery("select count(*) from " + table);
                rs.next();
                System.out.println("Table " + table + " row count: " + rs.getInt(1));
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    private int getCurrentId(String table) {
        try {
            ResultSet rs = sqlite.executeQuery("select nKey from " + table + " order by nKey desc");
            if (rs.next())
                return rs.getInt(1);
        } catch (SQLException ex) {
        }
        return 0;
    }

}
