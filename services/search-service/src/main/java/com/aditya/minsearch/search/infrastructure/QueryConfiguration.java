package com.aditya.minsearch.search.infrastructure;

@org.springframework.context.annotation.Configuration
@org.springframework.boot.context.properties.EnableConfigurationProperties({com.aditya.minsearch.search.application.QueryLimits.class,com.aditya.minsearch.search.application.RankingPolicy.class})
public class QueryConfiguration {}
