package com.gunjan;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Keyed<IN, KEY, ID> {
  private IN wrapped;
  private KEY key;
  private List<ID> ruleIds;
}
