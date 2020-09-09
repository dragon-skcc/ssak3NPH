package CleaningServiceYD;

public class KakaoAlerted extends AbstractEvent {

    private Long id;
    private Long cleanerId;
    private String cleanerName;
    private Long cleanerPNumber;

    public KakaoAlerted(){
        super();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getCleanerId() {
        return cleanerId;
    }

    public void setCleanerId(Long cleanerId) {
        this.cleanerId = cleanerId;
    }
    public String getCleanerName() {
        return cleanerName;
    }

    public void setCleanerName(String cleanerName) {
        this.cleanerName = cleanerName;
    }
    public Long getCleanerPNumber() {
        return cleanerPNumber;
    }

    public void setCleanerPNumber(Long cleanerPNumber) {
        this.cleanerPNumber = cleanerPNumber;
    }
}
