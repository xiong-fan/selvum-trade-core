package com.bizzan.bitrade.controller;

import com.bizzan.bitrade.dao.CoreSymbolMappingRepository;
import com.bizzan.bitrade.entity.CoreSymbolMapping;
import com.bizzan.bitrade.matching.constant.MatchingCommandType;
import com.bizzan.bitrade.matching.dto.MatchingCommand;
import com.bizzan.bitrade.service.ExchangeCoreCommandService;
import com.bizzan.bitrade.util.MessageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/matching/admin")
public class MatchingAdminController {
    @Autowired
    private CoreSymbolMappingRepository symbolMappingRepository;
    @Autowired
    private ExchangeCoreCommandService commandService;

    @PostMapping("/symbol/{symbol}/init")
    public MessageResult initSymbol(@PathVariable String symbol) {
        CoreSymbolMapping mapping = symbolMappingRepository.findBySymbol(symbol);
        if (mapping == null) {
            return MessageResult.error("symbol mapping not found");
        }
        MatchingCommand command = new MatchingCommand();
        command.setCommandId("ADD_SYMBOL-" + symbol);
        command.setCommandType(MatchingCommandType.ADD_SYMBOL);
        command.setBusinessKey(symbol);
        command.setSymbol(symbol);
        command.setCoreSymbolId(mapping.getCoreSymbolId());
        command.setTimestamp(System.currentTimeMillis());
        commandService.handle(command);
        return MessageResult.success("success");
    }

    @PostMapping("/user/{memberId}/init")
    public MessageResult initUser(@PathVariable Long memberId) {
        MatchingCommand command = new MatchingCommand();
        command.setCommandId("ADD_USER-" + memberId);
        command.setCommandType(MatchingCommandType.ADD_USER);
        command.setBusinessKey(String.valueOf(memberId));
        command.setMemberId(memberId);
        command.setTimestamp(System.currentTimeMillis());
        commandService.handle(command);
        return MessageResult.success("success");
    }
}
