    package ru.ufanet.meetingbot.service;
    
    import org.hibernate.Session;
    import org.hibernate.SessionFactory;
    import org.hibernate.cfg.Configuration;
    import org.springframework.stereotype.Component;
    import org.telegram.telegrambots.bots.TelegramLongPollingBot;
    import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
    import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage;
    import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
    import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
    import org.telegram.telegrambots.meta.api.objects.Message;
    import org.telegram.telegrambots.meta.api.objects.Update;
    import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
    import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
    import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
    import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
    import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
    import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
    import ru.ufanet.meetingbot.config.BotConfig;
    import ru.ufanet.meetingbot.repository.User;

    import java.util.*;

    @Component
    public class TelegramBot extends TelegramLongPollingBot {
        final BotConfig config;
        private static final String COMMAND_START = "/start";
    
        private static final String COMMAND_MEET = "/meet";
        private static final String COMMAND_INFO = "/info";
        private static final String COMMAND_HELP = "/help";
    
        private static final String DATE = "^(0[1-9]|[12][0-9]|3[01])\\.(0[1-9]|1[0-2])\\.(\\d{4})$";
        private static final String TIME = "^(09|1[0-6]):[0-5][0-9]$";
    
        private String inputDate;
        private String inputTime;
        private String inputLocation;
        private String inputPurpose;
    
        private static final String STATE_INITIAL = "initial";
        private static final String STATE_AWAITING_DATE = "awaiting_date";
        private static final String STATE_AWAITING_TIME = "awaiting_time";
        private static final String STATE_AWAITING_LOCATION = "awaiting_location";
        private static final String STATE_AWAITING_PURPOSE = "awaiting_purpose";
        private static final String STATE_CONFIRMATION = "confirmation";
    
        private String currentState = STATE_INITIAL;

        private String lastMeetingInfo;
    
    
        static final String HELP_TEXT = "Это бот для организации огранизации встречь сотрудников Уфанет";
        public TelegramBot(BotConfig config){
            this.config = config;
            List<BotCommand> listOfCommands = new ArrayList<>();
            listOfCommands.add(new BotCommand("/meet", "назначить встречу"));
            listOfCommands.add(new BotCommand("/info", "информация о встрече"));
            listOfCommands.add(new BotCommand("/help", "помощь"));
    
            try{
                this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(),null));
            }
            catch (TelegramApiException e){
    
            }
         }
        @Override
        public String getBotUsername() {
            return config.getBotName();
        }
    
        @Override
        public String getBotToken() {
            return config.getToken();
        }
    
        public void onUpdateReceived(Update update) {
            if (update.hasMessage() && update.getMessage().hasText()) {
                String messageText = update.getMessage().getText();
                long chatId = update.getMessage().getChatId();
    
                switch (currentState) {
                    case STATE_INITIAL:
                        handleInitialCommand(chatId, messageText);
                        break;
    
                    case STATE_AWAITING_DATE:
                        handleDateInput(chatId, messageText);
                        break;
    
                    case STATE_AWAITING_TIME:
                        handleTimeInput(chatId, messageText);
                        break;
    
                    case STATE_AWAITING_LOCATION:
                        handleLocationInput(chatId, messageText);
                        break;
    
                    case STATE_AWAITING_PURPOSE:
                        handlePurposeInput(chatId, messageText);
                        break;
                    case STATE_CONFIRMATION:
                        handleConfirmation(chatId, messageText);
                        break;
    
                    default:
                        sendMessage(chatId, "Неизвестное состояние.");
                        break;
                }
            }else if (update.hasCallbackQuery()) {
                // Обработка нажатий на кнопки
                String callbackData = update.getCallbackQuery().getData();
                long chatId = update.getCallbackQuery().getMessage().getChatId();

                if (callbackData.equals("confirm")) {
                    handleConfirmation(chatId, callbackData);
                }
            }

        }

        private void handleInitialCommand(long chatId, String command) {

            switch (command) {
                case COMMAND_START:
                    String greetingMessage = "Привет! Я - бот для организации встречей.\n\n" +
                            "Я помогу вам назначить встречу, предоставить информацию о встрече и дать помощь по необходимым командам.\n\n" +
                            "Для назначения встречи используйте команду /meet.\n" +
                            "Для получения информации о встрече используйте команду /info.\n" +
                            "Для получения помощи используйте команду /help.\n\n" +
                            "Чтобы начать, выберите одну из доступных команд.";

                    sendMessage(chatId, greetingMessage);
                    break;

                case COMMAND_MEET:
                    sendMessage(chatId, "Укажите дату в формате ДД.ММ.ГГГГ");
                    currentState = STATE_AWAITING_DATE;
                    break;

                case COMMAND_HELP:
                    sendMessage(chatId, HELP_TEXT);
                    break;

                case COMMAND_INFO:
                    lastMeetingInfo = getLastMeetingInfo();
                    if (lastMeetingInfo != null) {
                        sendMessage(chatId, lastMeetingInfo);
                    } else {
                        sendMessage(chatId, "На данный момент нет активных мероприятий. Хотите создать новое? Используйте команду /meet.");
                    }
                    break;

                default:
                    sendMessage(chatId, "Увы, такой команды нет...");
                    break;
            }
        }
    
        private void handleDateInput(long chatId, String input) {
            if (input.matches(DATE)) {
                inputDate = input;
                sendMessage(chatId, "Укажите время мероприятия: 09:00-17:00");
                currentState = STATE_AWAITING_TIME;
            } else {
                sendMessage(chatId, "Вы указали дату в неправильном формате!");
            }
        }
    
        private void handleTimeInput(long chatId, String input) {
            if (input.matches(TIME)) {
                inputTime = input;
                sendMessage(chatId, "Укажите место встречи:");
                currentState = STATE_AWAITING_LOCATION;
            } else {
                sendMessage(chatId, "Вы указали неправильное время!");
            }
        }
    
        private void handleLocationInput(long chatId, String input) {
            inputLocation = input;
            sendMessage(chatId, "Укажите цель встречи:");
            currentState = STATE_AWAITING_PURPOSE;
        }

        private void handlePurposeInput(long chatId, String input) {
            inputPurpose = input;

            // Создание информации о мероприятии
            StringBuilder sb = new StringBuilder();
            sb.append("Информация о мероприятии:\n");
            sb.append("\uD83D\uDCC5 Дата: ").append(inputDate).append("\n");
            sb.append("\u231A Время: ").append(inputTime).append("\n");
            sb.append("\uD83D\uDCCF Место: ").append(inputLocation).append("\n");
            sb.append("\uD83C\uDFAF Цель: ").append(inputPurpose);

            // Сохранение информации о мероприятии
            lastMeetingInfo = sb.toString();

            // Создание кнопки подтверждения
            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton confirmButton = new InlineKeyboardButton();
            confirmButton.setText("Подтвердить");
            confirmButton.setCallbackData("confirm");
            row.add(confirmButton);
            keyboardMarkup.setKeyboard(Collections.singletonList(row));

            // Отправка сообщения с информацией о мероприятии и кнопкой подтверждения
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(lastMeetingInfo);
            message.setReplyMarkup(keyboardMarkup);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                // Обработка ошибок при отправке сообщения
            }

            // Сбрасываем состояние бота в исходное
            currentState = STATE_INITIAL;
        }

        // Реализуйте метод getLastMeetingInfo
        private String getLastMeetingInfo() {
            return lastMeetingInfo;
        }
        private void sendPoll(long chatId, String question, List<String> options) {
            SendPoll sendPoll = new SendPoll();
            sendPoll.setChatId(String.valueOf(chatId));
            sendPoll.setQuestion(question);
            sendPoll.setOptions(options);
            sendPoll.setType("regular");

            try {
                execute(sendPoll);
            } catch (TelegramApiException e) {
                // Обработка ошибок при отправке голосования
            }
        }
        private void handleConfirmation(long chatId, String input) {
            if (input.equals("confirm")) {
                // Отправка голосования всем участникам группы
                String question = "Удобно ли вам?"; // Вопрос голосования
                List<String> options = Arrays.asList("Да", "Нет"); // Варианты ответов
                sendPoll(chatId, question, options);

                // Отправка сообщения с информацией о мероприятии
                StringBuilder sb = new StringBuilder();
                sb.append("Информация о мероприятии:\n");
                sb.append("\uD83D\uDCC5 Дата: ").append(inputDate).append("\n");
                sb.append("\u231A Время: ").append(inputTime).append("\n");
                sb.append("\uD83D\uDCCF Место: ").append(inputLocation).append("\n");
                sb.append("\uD83C\uDFAF Цель: ").append(inputPurpose);

                sendMessage(chatId, sb.toString());
            }

            // Сбрасываем состояние бота в исходное
            currentState = STATE_INITIAL;
        }
    
    
    
    
    
    
    
        private void sendMessage(long chatId, String textToSend){
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(textToSend);
    
            try{
                execute(message);
            }
            catch (TelegramApiException e ){
    
            }
        }
    
        private void registerUser(String name){
    
            User user = new User(name);
    
        }
    }
