package CleaningServiceYD;

public class CleanerRegistered extends AbstractEvent {

    private Long id;
    private Long CleanerID;
    private String CleanerName;
    private Long CleanerPNumber;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getCleanerId() {
        return CleanerID;
    }

    public void setCleanerId(Long CleanerID) {
        this.CleanerID = CleanerID;
    }
    public String getCleanerName() {
        return CleanerName;
    }

    public void setCleanerName(String CleanerName) {
        this.CleanerName = CleanerName;
    }
    public Long getCleanerPNumber() {
        return CleanerPNumber;
    }

    public void setCleanerPNumber(Long CleanerPNumber) {
        this.CleanerPNumber = CleanerPNumber;
    }
}