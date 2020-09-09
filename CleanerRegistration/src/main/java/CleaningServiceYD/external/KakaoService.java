
package CleaningServiceYD.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="KakaoAPI", url="http://KakaoAPI:8080")
public interface KakaoService {

    @RequestMapping(method= RequestMethod.POST, path="/kakaos")
    public void kakaoRequest(@RequestBody Kakao kakao);

}