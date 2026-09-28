FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY VacacionesService.csproj ./
RUN dotnet restore
COPY . .
RUN dotnet publish -c Release -o /app/publish --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:8.0
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/publish .
EXPOSE 8085
ENV ASPNETCORE_URLS=http://+:8085
ENTRYPOINT ["dotnet", "VacacionesService.dll"]
