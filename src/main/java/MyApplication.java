import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;

@RestController
@CrossOrigin
@EnableAutoConfiguration
public class MyApplication {
    public static class Image {
        public String id;
        public String src;
        public String time;

        public Image(Figure fig) {
            id = String.valueOf(fig.getFileId());
            src = String.format("figure%d?id=%d", fig.getFigure(), fig.getFileId());
            time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(fig.getTimestamp());
        }
    }

    public static class FigInfo{
        public int figure;
        public String name;
        public List<Image> id_list;
        public FigInfo(int fid){
            figure = fid;
            name = String.format("figure-id%d", fid);
            id_list = new ArrayList<>();
        }
    }

    static SessionFactory sessionFactory;
    static Session session;

    static synchronized <R> R sqlite(Function<Session, R> func) {
        if (session == null) {
            session = sessionFactory.openSession();
        }
        Transaction transaction = session.beginTransaction();
        R value = func.apply(session);
        transaction.commit();
        return value;
    }

    @RequestMapping(value = "/figure{fid}", produces = "image/svg+xml")
    ResponseEntity<byte[]> figure(@PathVariable(value = "fid") int fid, @RequestParam(name = "id") int id) {
        Blob blob = sqlite((session) -> session.get(Blob.class, id));
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", blob.getContentType());
        return new ResponseEntity<>(blob.getBlob(), headers, HttpStatus.OK);
    }

    @RequestMapping("/listfig")
    List<FigInfo> listfig() {
        List<Figure> figures = sqlite((session) -> {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<Figure> query = builder.createQuery(Figure.class);
            Root<Figure> root = query.from(Figure.class);
            query.select(root);
            query.orderBy(builder.desc(root.get("timestamp")));
            return session.createQuery(query).getResultList();
        });

        SortedMap<Integer, ArrayList<Figure>> res = new TreeMap<>();
        for (Figure fig : figures) {
            int fid = fig.getFigure();
            ArrayList<Figure> arrayList = res.computeIfAbsent(fid, k -> new ArrayList<>());
            arrayList.add(fig);
        }
        List<FigInfo> responses = new ArrayList<>();
        for (int fid : res.keySet()) {
            FigInfo figInfo = new FigInfo(fid);
            for (Figure fig : res.get(fid)) {
                figInfo.id_list.add(new Image(fig));
            }
            responses.add(figInfo);
        }
        return responses;
    }

    @RequestMapping("/addfig")
    String addfig(@RequestParam("fig") int fig, @RequestParam("file") MultipartFile file) throws IOException {
        Blob blob = new Blob();
        blob.setContentType(file.getContentType());
        blob.setBlob(file.getBytes());
        sqlite((session) -> {
            session.save(blob);
            return null;
        });
        Figure figure = new Figure();
        figure.setFigure(fig);
        figure.setFileId(blob.getId());
        sqlite((session) -> {
            session.save(figure);
            return null;
        });
        return "OK";
    }

    public static void main(String[] args) {
        Configuration configuration = new Configuration().configure();
        configuration.addAnnotatedClass(Blob.class);
        configuration.addAnnotatedClass(Figure.class);
        sessionFactory = configuration.buildSessionFactory();
        SpringApplication.run(MyApplication.class, args);
    }

}