package CleaningServiceYD;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Message_table")
public class Message {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long requestId;
    private String status;

    private Long CleanerID;
    private String CleanerName;
    private Long CleanerPNumber;

    @PostPersist
    public void onPostPersist(){
        MessageAlerted messageAlerted = new MessageAlerted();
        messageAlerted.setRequestId(getRequestId());
        messageAlerted.setStatus("MessageSaved");
        BeanUtils.copyProperties(this, messageAlerted);
        messageAlerted.publishAfterCommit();
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
