package CleaningServiceYD;


public class CleanerRegistered extends AbstractEvent{
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

    public Long getCleanerID() {
        return CleanerID;
    }

    public void setCleanerID(Long cleanerID) {
        CleanerID = cleanerID;
    }

    public String getCleanerName() {
        return CleanerName;
    }

    public void setCleanerName(String cleanerName) {
        CleanerName = cleanerName;
    }

    public Long getCleanerPNumber() {
        return CleanerPNumber;
    }

    public void setCleanerPNumber(Long cleanerPNumber) {
        CleanerPNumber = cleanerPNumber;
    }
}
