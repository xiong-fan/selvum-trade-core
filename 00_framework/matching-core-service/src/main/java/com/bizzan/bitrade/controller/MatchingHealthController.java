package com.bizzan.bitrade.controller;

import com.bizzan.bitrade.engine.ExchangeCoreLifecycle;
import com.bizzan.bitrade.util.MessageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/matching")
public class MatchingHealthController {
    @Autowired
    private ExchangeCoreLifecycle lifecycle;

    @GetMapping("/health")
    public MessageResult health() {
        MessageResult result = MessageResult.success("success");
        result.setData(lifecycle.isStarted());
        return result;
    }
}
