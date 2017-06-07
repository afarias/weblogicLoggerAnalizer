package com.oracle.ocs.tools.loggeranalyzer;

import com.oracle.ocs.tools.loggeranalyzer.analyser.InferenceMachine;
import com.oracle.ocs.tools.loggeranalyzer.analyser.LogAnalyser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class is responsible for creating instances of a LogFile, based on multiple algorithms of Record fields
 * detection.
 *
 * @author Andrés Farías on 6/2/17.
 */
public class LogFileFactory {

    private static final Logger logger = LoggerFactory.getLogger(LogFileFactory.class);

    /** The logfile from which records are read and a configuration for the reading is defined */
    private LogFileConfiguration logFileConfiguration;

    /** A simple utility configured for this log */
    private TokenUtils tokenUtils;

    /** A log analyser for the log */
    private LogAnalyser logAnalyser = new LogAnalyser();

    /**
     * The basic constructor that requires only the Log Configuration.
     *
     * @param logFileConfiguration The Log configuration.
     */
    public LogFileFactory(LogFileConfiguration logFileConfiguration) {
        this.logFileConfiguration = logFileConfiguration;
        tokenUtils = new TokenUtils(logFileConfiguration.getInitDelimiter(), logFileConfiguration.getEndDelimiter());
    }

    /**
     * This method is responsible for creating a LogFile based on the configuration log file and other algorithms.
     *
     * @return A <code>LogFile</code> object representing the read Log file.
     */
    public LogFile createLogFile() throws IOException {

        /* The log is created from its configuration file */
        LogFile logFile = new LogFile(this.logFileConfiguration);

        /* Before parsing the log for its records, some little IA is performed */
        new InferenceMachine(logFile).inferTokenPositions();

        /* The log is read to extract its log records. Their object representation then is associated with the LogFile */
        List<LogRecord> logRecords = parseLogRecords();
        logFile.setLogRecords(logRecords);

        return logFile;
    }

    /**
     * This method is responsible for reading every line on the log file and process its data relating it.
     *
     * @return A list of records parsed.
     */
    private List<LogRecord> parseLogRecords() throws IOException {

        /* Records are created from the particular info of the log File */
        FileReader fileReader = new FileReader(this.logFileConfiguration.getLogFile());
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;

        /*
         * For each Log Record one or more lines are to be read. The Log Record will have only one Header Line, and
         * zero or more lines that follows, as stack traces.
         */
        LogRecord logRecord = new LogRecord("");
        List<LogRecord> logRecords = new ArrayList<>();
        while ((line = bufferedReader.readLine()) != null) {

            /* If the read line is a header, then this is a new log record and it's created an initialized */
            if (isLogRecordHeader(line)) {
                logRecord = new LogRecord(line);
                logRecords.add(logRecord);

                /* Every known Token is extracted depending in its known position */
                List<LogRecordToken> logRecordTokens = parseLogRecordTokens(logRecord.getHeaderLine(), this.logFileConfiguration.getTokenPositions());
                logRecord.assignTokens(logRecordTokens);

                int size = logRecords.size();
                if (size % 100 == 0) {
                    logger.info(size + " records read.");
                }
                ;
            }

            /* Otherwise, the line belongs to the current log record, and it's no processed */
            else {
                logRecord.addLine(line);
            }
        }

        logger.debug("Log records parsed: {}", logRecords.size());
        return logRecords;
    }

    /**
     * This method is responsible for parsing the log record line and extract the tokens defined on the Map of token
     * positions.
     *
     * @param recordLine     The log record line from which the line to be parsed is obtained.
     * @param tokenPositions The information about the position of every given token.
     *
     * @return A list of tokens parsed.
     */
    protected List<LogRecordToken> parseLogRecordTokens(String recordLine, Map<TokenType, Integer> tokenPositions) {

        /* The tokens known to have a specific position are iterated */
        List<LogRecordToken> logRecordTokens = new ArrayList<>();
        for (TokenType tokenType : tokenPositions.keySet()) {
            String tokenValue = tokenUtils.extractTokenAtPosition(recordLine, tokenPositions.get(tokenType));

            /* The token is created and added to the resulting list */
            LogRecordToken aToken = new LogRecordToken(tokenType, tokenValue);
            logRecordTokens.add(aToken);
        }

        return logRecordTokens;
    }

    /**
     * This method is responsible for determining if the given <code>line</code> is a log record header or not. This is
     * based on the presence on the token extraction and trying to match the token extracted with their corresponding
     * type.
     *
     * @param line The line to be parsed and analysed for determining if it's about a Header Record.
     *
     * @return <code>true</code> if the line represents a log record header and <code>false</code> otherwise.
     */
    protected boolean isLogRecordHeader(String line) {

        Map<TokenType, Integer> tokenPositions = this.logFileConfiguration.getTokenPositions();
        int numberOfTokens = tokenPositions.keySet().size();

        List<LogRecordToken> logRecordTokens = tokenUtils.extractTokensFromLine(line);
        int tokenExtracted = logRecordTokens.size();

        /* If there are less tokens than expected (tokenPositions.size()) then it is not a header */
        if (numberOfTokens > tokenExtracted) {
            return false;
        }

        /* Now, the line can have more tokens than expected, but do they match? */
        List<LogRecordToken> parsedTokens;
        try {
            parsedTokens = parseLogRecordTokens(line, tokenPositions);
        } catch (IllegalArgumentException iae) {
            return false;
        }

        return parsedTokens.size() == numberOfTokens;
    }

    public Map<TokenType, Integer> getTokenPositions() {
        return logFileConfiguration.getTokenPositions();
    }
}
