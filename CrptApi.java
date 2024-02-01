package ru.neka;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Builder;
import lombok.Data;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class CrptApi {
    

    private final int requestLimit;
    private final AtomicInteger requestCounter;
    private final ScheduledExecutorService scheduler;
    private final Gson gson;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final HttpClient httpClient;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.requestCounter = new AtomicInteger(0);
        this.gson = new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
        this.httpClient = HttpClient.newHttpClient();

        // Периодически обнуляем счетчик запросов
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduler.scheduleAtFixedRate(() -> {
            lock.lock();
            try {
                requestCounter.set(0);
                System.out.println("Счетчик обнулен.");
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }, 0, 1, timeUnit);
    }

    public void createDocument(Document document, String signature) {
        lock.lock();
        try {
            if (requestCounter.get() >= requestLimit) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            // Создание json
            String jsonRequest = gson.toJson(document);
            HttpRequest postRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                    .build();


            HttpResponse<String> response = httpClient.send(postRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Увеличение счетчика запросов только при успешном ответе
                requestCounter.incrementAndGet();
                System.out.println("Запрос выполнен. Счетчик: " + requestCounter.get());
            } else {
                // Обработка ошибки, если не получен ожидаемый статус код
                requestCounter.incrementAndGet();
                System.out.println("Ошибка выполнения запроса. Статус код: " + response.statusCode());
            }

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            condition.signalAll();
            lock.unlock();  // Освобождение блокировки
        }

    }


    // Внутренние классы для представления документа
    @Data
    @Builder
    public static class Document {

        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public Date production_date;
        public String production_type;
        public List<Product> products;
        public Date reg_date;
        public String reg_number;

    }

    @Data
    @Builder
    public static class Description {

        public String participantInn;

    }

    @Data
    @Builder
    public static class Product {

        public String certificate_document;
        public Date certificate_document_date;
        public String certificate_document_number;
        public String owner_inn;
        public String producer_inn;
        public Date production_date;
        public String tnved_code;
        public String uit_code;
        public String uitu_code;

    }

    public static void main(String[] args) {
        Document document = Document.builder()
                .description(Description.builder().participantInn("1234567890").build())
                .doc_id("DOC123")
                .doc_status("Pending")
                .doc_type("LP_INTRODUCE_GOODS")
                .importRequest(true)
                .owner_inn("0987654321")
                .participant_inn("1234567890")
                .producer_inn("5678901234")
                .production_date(new Date())
                .production_type("ExampleType")
                .products(List.of(Product.builder()
                        .certificate_document("Cert123")
                        .certificate_document_date(new Date())
                        .certificate_document_number("Cert456")
                        .owner_inn("0987654321")
                        .producer_inn("5678901234")
                        .production_date(new Date())
                        .tnved_code("Tnved789")
                        .uit_code("Uit456")
                        .uitu_code("Uitu123")
                        .build()))
                .reg_date(new Date())
                .reg_number("REG123")
                .build();
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 2);

        while (true) {
            crptApi.createDocument(document, "podpis");
        }

    }
    }











