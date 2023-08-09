package rules;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gunjan.Rule;

import java.util.ArrayList;
import java.util.List;

class Rules {
    @JsonProperty("data")
    private List<Rule> data = new ArrayList<>();

    public List<Rule> getData() {
        return data;
    }

    public void setData(List<Rule> data) {
        this.data = data;
    }
}
