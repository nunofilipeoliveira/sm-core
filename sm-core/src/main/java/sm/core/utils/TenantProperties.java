package sm.core.utils;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sm.core")
public class TenantProperties {

	private Map<String, Map<String, String>> tenant_id;

	public Map<String, Map<String, String>> getTenant_id() {
		return tenant_id;
	}

	public void setTenant_id(Map<String, Map<String, String>> tenant_id) {
		this.tenant_id = tenant_id;
	}

	  public static class Tenant {
        private String name;
        // getters e setters

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
    }
}
