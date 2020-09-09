package CleaningServiceYD;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Kakao_table")
public class Kakao {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long cleanerId;
    private String cleanerName;
    private Long cleanerPNumber;

    @PostPersist
    public void onPostPersist(){
        KakaoAlerted kakaoAlerted = new KakaoAlerted();
        BeanUtils.copyProperties(this, kakaoAlerted);
        kakaoAlerted.publishAfterCommit();


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
