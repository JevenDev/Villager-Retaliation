package com.jvn.villagerretaliation.dialogue.forced.trade;

public final class TradeRefreshForcedDialogue {
    public static final String TRADE_REFRESH_DEFINITION_ID = "trade_refresh";
    public static final String TRADE_REFRESH_TRADE_OPTION_ID = "trade_refresh.trade";
    public static final String TRADE_REFRESH_READY_MESSAGE_KEY = "trade_refresh.ready";
    public static final String TRADE_REFRESH_READY_INTERJECTION_MESSAGE_KEY = "trade_refresh.ready_interjection";
    public static final String TRADE_REFRESH_READY_THEFT_INTERJECTION_MESSAGE_KEY = "trade_refresh.ready_theft_interjection";
    public static final String TRADE_REFRESH_READY_OPTIONS_ID = "trade_refresh.ready_options";
    public static final String TRADE_REFRESH_REVERED_OPTIONS_ID = "trade_refresh.revered_options";
    public static final String TRADE_REFRESH_SPECIAL_ORDER_SELECT_OPTIONS_ID = "trade_refresh.special_order_select_options";
    public static final String TRADE_REFRESH_SPECIAL_ORDER_CONFIRM_OPTIONS_ID = "trade_refresh.special_order_confirm_options";
    public static final String TRADE_REFRESH_SPECIAL_ORDER_STATUS_OPTIONS_ID = "trade_refresh.special_order_status_options";
    public static final String TRADE_REFRESH_SURPRISE_OPTION_ID = "trade_refresh.surprise_me";
    public static final String TRADE_REFRESH_SPECIAL_ORDER_OPTION_ID = "trade_refresh.special_order";
    public static final String TRADE_REFRESH_CONFIRM_SPECIAL_ORDER_OPTION_ID = "trade_refresh.confirm_special_order";
    public static final String TRADE_REFRESH_REQUIREMENTS_OPTION_ID = "trade_refresh.requirements";

    private TradeRefreshForcedDialogue() {
    }

    public static boolean isDefinition(String definitionId) {
        return TRADE_REFRESH_DEFINITION_ID.equals(definitionId);
    }

    public static boolean isTradeOption(String definitionId, String optionId) {
        return isDefinition(definitionId) && TRADE_REFRESH_TRADE_OPTION_ID.equals(optionId);
    }

    public static boolean isSurpriseOption(String definitionId, String optionId, int offerIndex) {
        return isDefinition(definitionId)
                && TRADE_REFRESH_SURPRISE_OPTION_ID.equals(optionId)
                && offerIndex >= 0;
    }

    public static boolean isSpecialOrderOption(String definitionId, String optionId, int offerIndex) {
        return isDefinition(definitionId)
                && TRADE_REFRESH_SPECIAL_ORDER_OPTION_ID.equals(optionId)
                && offerIndex >= 0;
    }

    public static boolean isConfirmSpecialOrderOption(
            String definitionId,
            String optionId,
            int offerIndex,
            String selectedDefinitionId) {
        return isDefinition(definitionId)
                && TRADE_REFRESH_CONFIRM_SPECIAL_ORDER_OPTION_ID.equals(optionId)
                && offerIndex >= 0
                && selectedDefinitionId != null
                && !selectedDefinitionId.isBlank();
    }
}
