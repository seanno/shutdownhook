#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>

#include "minja/minja.hpp"
#include <nlohmann/json.hpp>

static std::string read_input(const std::string & path) {
    if (path == "-") {
        return std::string(std::istreambuf_iterator<char>(std::cin),
                           std::istreambuf_iterator<char>());
    }
    std::ifstream f(path);
    if (!f) {
        throw std::runtime_error("cannot open file: " + path);
    }
    return std::string(std::istreambuf_iterator<char>(f),
                       std::istreambuf_iterator<char>());
}

int main(int argc, char * argv[]) {
    if (argc != 3) {
        std::cerr << "Usage: " << argv[0] << " <template> <context.json>\n"
                  << "  Use '-' for either argument to read from stdin\n";
        return 1;
    }

    const std::string tmpl_path = argv[1];
    const std::string json_path = argv[2];

    if (tmpl_path == "-" && json_path == "-") {
        std::cerr << "Error: only one argument may be '-'\n";
        return 1;
    }

    try {
        const std::string tmpl_str = read_input(tmpl_path);
        const std::string json_str = read_input(json_path);

        const nlohmann::ordered_json context_json = nlohmann::ordered_json::parse(json_str);

        auto tmpl = minja::Parser::parse(tmpl_str, {});
        auto ctx  = minja::Context::make(minja::Value(context_json));
        std::cout << tmpl->render(ctx);
        return 0;
    } catch (const std::exception & e) {
        std::cerr << "Error: " << e.what() << "\n";
        return 1;
    }
}
