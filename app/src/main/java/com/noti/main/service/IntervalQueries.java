package com.noti.main.service;

public class IntervalQueries {
    public static class Query {
        private String Package;
        private long Timestamp;

        public String getPackage() {
            return Package;
        }

        public void setPackage(String aPackage) {
            Package = aPackage;
        }

        public long getTimestamp() {
            return Timestamp;
        }

        public void setTimestamp(long timestamp) {
            Timestamp = timestamp;
        }
    }

    public static class SmsQuery {
        private String Number;
        private String Content;
        private long TimeStamp;

        public String getNumber() {
            return Number;
        }

        public String getContent() {
            return Content;
        }

        public long getTimeStamp() {
            return TimeStamp;
        }

        public void setNumber(String number) {
            Number = number;
        }

        public void setContent(String content) {
            Content = content;
        }

        public void setTimeStamp(long timeStamp) {
            TimeStamp = timeStamp;
        }
    }

    public static class TelecomQuery {
        private String Number;
        private long TimeStamp;

        public String getNumber() {
            return Number;
        }

        public long getTimeStamp() {
            return TimeStamp;
        }

        public void setNumber(String number) {
            Number = number;
        }

        public void setTimeStamp(long timeStamp) {
            TimeStamp = timeStamp;
        }
    }
}
