package com.example.SpringDemoBot.service;


import com.example.SpringDemoBot.config.BotConfig;
import com.example.SpringDemoBot.model.*;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.Reader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.UpdatesReader;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdsRepository adsRepository;

    @Autowired
    private ProverbsRepository proverbsRepository;

    @Autowired
    private SecretsRepository secretsRepository;

    private String answer;

    final BotConfig config;

    static final String HELP_TEXT= "This bot is created for demonstrate Spring capabilities.\n\n" +
            "You can execute commands from the main menu on the left or by typing command:\n\n" +
            "Type /start to see a welcome message\n\n"+
            "Type /mydata to see data stored about yourself\n\n"+
            "Type /deletedata to delete your data\n\n"+
            "Type /help to see this message again\n\n";


    static final String ESC_BUTTON = "Позанимались, можно и отдохнуть!";

    static final String ERROR_TEXT = "Error occurred: ";

    private int d;

    private int counter;


    private List <Long> processingUsers = new ArrayList<>();

    private List <Long> processingUsersForSecret = new ArrayList<>();

    public TelegramBot(BotConfig config){
        this.config=config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "get a welcome message"));
        listOfCommands.add(new BotCommand("/mydata","get your data stored"));
        listOfCommands.add(new BotCommand("/deletedata", "delete my data"));
        listOfCommands.add(new BotCommand("/help", "info how to use this bot"));


        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));

        }
        catch (TelegramApiException e){
            log.error("Error setting bot's command list: " + e.getMessage());
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

    @Override
    public void onUpdateReceived(Update update) {

if(update.hasMessage()&& update.getMessage().hasText()){

    String messageText = update.getMessage().getText();
    long chatID = update.getMessage().getChatId();

    if(messageText.contains("/send") && config.getOwnerId()==chatID){
        var textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
        var users =userRepository.findAll();
        for (User user: users){
            prepareAndSendMessage(user.getChatId(), textToSend);
        }
    }

    else {
        switch (messageText) {
            case "/start":

                registerUser(update.getMessage());
                startCommandReceived(chatID, update.getMessage().getChat().getFirstName());
                break;
            case "/help":
                prepareAndSendMessage(chatID, HELP_TEXT);

                break;


            case "Пример для Ани":
                processingUsers.add(update.getMessage().getFrom().getId());
                prepareAndSendMessage(chatID, generateMathForAnn());
                prepareAndSendMessage(chatID, "Напиши результат");

                break;

            case "Пример для Полины":
                processingUsers.add(update.getMessage().getFrom().getId());
                prepareAndSendMessage(chatID, generateMathForPolly());
                prepareAndSendMessage(chatID, "Напиши результат");


                break;

            case "Шутка":
                prepareAndSendMessage(chatID, "Пока не смешно(");


                break;

            case "Загадка":
                counter=3;
                processingUsersForSecret.add(update.getMessage().getFrom().getId());
                int j = (int)(Math.random()*19)+1;
                Optional<Secrets> secrets = secretsRepository.findById(Long.valueOf(j));
                Secrets secret = secrets.get();
                answer =secret.getAnswer();
                prepareAndSendMessage(chatID, secret.getSecret());
                prepareAndSendMessage(chatID, "Напиши что это!\n" +
                        "У тебя 3 попытки.");



                break;

            case "Пословица":
                int i = (int)(Math.random()*9)+1;
                Optional<Proverb> proverbs = proverbsRepository.findById(Long.valueOf(i));
                Proverb proverb = proverbs.get();

                prepareAndSendMessage(chatID, proverb.getProverb());


                break;

            case "/mydata":
                prepareAndSendMessage(chatID, getUserData(update.getMessage()));
                break;

            case "/deletedata":
                deleteUserData(update.getMessage());
                prepareAndSendMessage(chatID, "Ваши данные удалены!");
                break;

            default:
                handleSimpleMessage(update);

                break;





        }
    }
}
 else if(update.hasCallbackQuery()) {

     String callbackData = update.getCallbackQuery().getData();
     long messageId = update.getCallbackQuery().getMessage().getMessageId();
     long chatId = update.getCallbackQuery().getMessage().getChatId();


      if (callbackData.equals(ESC_BUTTON)){
         String text = "Позанимались, можно и отдохнуть!";
         executeEditMessageText(text, chatId, messageId);
         processingUsers.clear();
     }


}


    }



    private void registerUser(Message msg){

        if(userRepository.findById(msg.getChatId()).isEmpty()){

            var chatId =msg.getChatId();
            var chat =msg.getChat();

            User user = new User();

            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

            userRepository.save(user);
            log.info("user saved: " + user);

        }

    }
    private String getUserData(Message msg){



            var chatId =msg.getChatId();
            User user = userRepository.findById(chatId).get();

            String name = user.getFirstName();
            String lastName = user.getLastName();
            String userName = user.getUserName();
            Long chatID = user.getChatId();
            Timestamp registeredAt = user.getRegisteredAt();
return "Ваши данные :\n" + name + " " + lastName + "\n" + "Hик: " + userName + "\n" + "ID: " + chatID + "\n" + "Время регистрации: " + registeredAt;

    }
    private void deleteUserData(Message msg){

        var chatId =msg.getChatId();
        User user = userRepository.findById(chatId).get();

            userRepository.delete(user);
            log.info("user deleted: " + user);

    }

    private void startCommandReceived(long chatID, String name) {


        String answer = EmojiParser.parseToUnicode("Привет, " + name + ", рад видеть тебя!" + ":blush:");

        log.info("Replied to user " + name);

        sendMessage(chatID, answer);
    }
    private void sendMessage(long chatId, String textToSend) {

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();

        row.add("Пример для Ани");
        row.add("Пример для Полины");


        keyboardRows.add(row);

        row = new KeyboardRow();

        row.add("Шутка");
        row.add("Загадка");
        row.add("Пословица");

        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);

        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);

    }

    private void executeEditMessageText(String text, long chatId, long messageId){
        EditMessageText message = new EditMessageText();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setMessageId((int)messageId);
        try {
            execute(message);
        }
        catch (TelegramApiException e){
            log.error(ERROR_TEXT + e.getMessage());

        }
    }
    private void executeMessage(SendMessage message){
        try {
            execute(message);
        }
        catch (TelegramApiException e){
            log.error(ERROR_TEXT + e.getMessage());

        }
    }
    private void prepareAndSendMessage(long chatId, String textToSend){
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        executeMessage(message);
    }
    @Scheduled(cron = "${cron.scheduler1}")
    private void sendAds1(){

        Optional<Ads> ads = adsRepository.findById(Long.valueOf("1"));
        Ads ad = ads.get();
        var users =userRepository.findAll();
            for (User user: users) {
                prepareAndSendMessage(user.getChatId(), ad.getAd());
            }

    }
    @Scheduled(cron = "${cron.scheduler2}")
    private void sendAds2(){

        Optional<Ads> ads = adsRepository.findById(Long.valueOf("2"));
        Ads ad = ads.get();
        var users =userRepository.findAll();
        for (User user: users) {
            prepareAndSendMessage(user.getChatId(), ad.getAd());
        }
    }
    @Scheduled(cron = "${cron.scheduler3}")
    private void sendAds3(){


        Optional<Ads> ads = adsRepository.findById(Long.valueOf("3"));
        Ads ad = ads.get();
        var users =userRepository.findAll();
        for (User user: users) {
            prepareAndSendMessage(user.getChatId(), ad.getAd());
        }

    }
    @Scheduled(cron = "${cron.scheduler4}")
    private void sendAds4(){

        Optional<Ads> ads = adsRepository.findById(Long.valueOf("4"));
        Ads ad = ads.get();
        var users =userRepository.findAll();
        for (User user: users) {
            prepareAndSendMessage(user.getChatId(), ad.getAd());
        }
    }

    private String generateMathForAnn(){
       int a = (int)(Math.random()*14) +1;
       int b = (int)(Math.random()*14) +1;
       int c = (int)(Math.random()*10);
       int e = (int)(Math.random()*9)+1;

       String str;
       if(c%2==0)
       { d = a + b + e;
      return str = a +" + " + b + " + " + e;}
       else if(a>=b) {
            d = a - b + e;
        return str = a + " - " + b + " + " + e;
       }
       else {
               d= b - a + e;
       return str = b + " - " + a + " + " + e;
       }

    }
    private String generateMathForPolly(){
        int a = (int)(Math.random()*999) +1;
        int b = (int)(Math.random()*999) +1;
        int c = (int)(Math.random()*10);
        int e = (int)(Math.random()*9)+1;
        int f = (int)(Math.random()*9)+1;


        String str;
        if(c<=3)
        { d = a + b;
            return str = a +" + " + b;}
        else if(c>3 && c<=6) {
            if (a >= b) {
                d = a - b;
                return str = a + " - " + b;
            } else {
                d = b - a;
                return str = b + " - " + a;
            }
        }
        else {
            d = e * f;
            return str = e + " * " + f;
        }
    }
    private void handleSimpleMessage(Update update) {
        long chatID = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();
        if (processingUsers.contains(userId)){
            String answer = update.getMessage().getText();
            int i =Integer.parseInt(answer);
            if (i==d){
                prepareAndSendMessage(chatID, "Умница!");
                processingUsers.remove(userId);
            }
            else {
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatID));
                message.setText("Неправильно, попробуй снова");
                InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
                List<InlineKeyboardButton> rowInLine = new ArrayList<>();
                var escButton = new InlineKeyboardButton();

                escButton.setText("Хватит на сегодня");
                escButton.setCallbackData(ESC_BUTTON);

                rowInLine.add(escButton);

                rowsInLine.add(rowInLine);

                markupInLine.setKeyboard(rowsInLine);
                message.setReplyMarkup(markupInLine);

                executeMessage(message);
            }
        }
        else if (processingUsersForSecret.contains(userId)) {
            String yourAnswer = update.getMessage().getText();

            if (answer.equalsIgnoreCase(yourAnswer)) {
                prepareAndSendMessage(chatID, "Правильно!");
                processingUsersForSecret.remove(userId);
            } else {
                counter--;

                if (counter == 2) {
                    SendMessage message = new SendMessage();
                    message.setChatId(String.valueOf(chatID));
                    message.setText("А вот и нет! У тебя осталось 2 попытки!");
                    executeMessage(message);
                }
                else if (counter == 1) {
                    SendMessage message = new SendMessage();
                    message.setChatId(String.valueOf(chatID));
                    message.setText("Снова нет! У тебя осталась 1 попытка!");
                    executeMessage(message);
                }
                else if (counter ==0) {
                    SendMessage message = new SendMessage();
                    message.setChatId(String.valueOf(chatID));
                    message.setText("Неа! Правильный ответ: "+ answer);
                    executeMessage(message);
                    processingUsersForSecret.remove(userId);
                }

            }
        }
        else {
            prepareAndSendMessage(chatID, "Простите, данная команда не поддерживается!");
        }
    }
}
